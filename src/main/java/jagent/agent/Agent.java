package jagent.agent;

import jagent.graph.Admission;
import jagent.graph.Graph;
import jagent.graph.NodeKind;
import jagent.graph.NodeState;
import jagent.graph.Scheduler;
import jagent.json.Json;
import jagent.mem.Compressor;
import jagent.mem.Memory;
import jagent.mem.Tokens;
import jagent.llm.ChatClient;
import jagent.llm.Delta;
import jagent.llm.Message;
import jagent.llm.Router;
import jagent.llm.ToolCall;
import jagent.llm.Turn;
import jagent.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class Agent {

    public interface Observer {
        void onStep(int step);
        void onToolCall(int step, ToolCall call);
        void onToolResult(int step, ToolCall call, String result);
        default void onBid(int step, Auction.Award a) {}
        default void onCompress(int step, long before, long after, String reason) {}
    }

    private static final Observer SILENT = new Observer() {
        public void onStep(int step) {}
        public void onToolCall(int step, ToolCall call) {}
        public void onToolResult(int step, ToolCall call, String result) {}
    };

    private final Router router;
    private final ToolRegistry tools;
    private final Graph graph;
    private final Scheduler sched;
    private final List<Message> convo = new ArrayList<>();
    private final Consumer<String> onText;
    private final Observer obs;
    private final int maxSteps;
    private final Auction auction;
    private final Budget budget;
    private final Governor governor;
    private final String parentNode;
    private final String stable;
    private String goal = "";
    private Memory mem;
    private boolean compress;
    private long compressThreshold = 6_000;
    private long inTokens, outTokens;
    private long foldedTotal;
    private int steps;

    public Agent(ChatClient client, ToolRegistry tools, Graph graph, Scheduler sched, String system,
                 Consumer<String> onText, Observer obs, int maxSteps) {
        this(new Router(client, client), tools, graph, sched, system, onText, obs, maxSteps,
                null, null, null, null);
    }

    public Agent(Router router, ToolRegistry tools, Graph graph, Scheduler sched, String system,
                 Consumer<String> onText, Observer obs, int maxSteps,
                 Auction auction, Budget budget, Governor governor, String parentNode) {
        this.router = router;
        this.tools = tools;
        this.graph = graph;
        this.sched = sched;
        this.onText = onText == null ? s -> {} : onText;
        this.obs = obs == null ? SILENT : obs;
        this.maxSteps = maxSteps;
        this.auction = auction;
        this.budget = budget;
        this.governor = governor;
        this.parentNode = parentNode;
        this.stable = system == null ? "" : system;
        convo.add(Message.system(this.stable));
    }

    public Agent memory(Memory mem) {
        this.mem = mem;
        return this;
    }

    public Agent compression(boolean on) {
        this.compress = on;
        return this;
    }

    public Agent compressThreshold(long tokens) {
        this.compressThreshold = Math.max(64, tokens);
        return this;
    }

    private String assembleSystem() {
        return SystemPrompt.assemble(stable, goal,
                mem == null ? "" : mem.digest(),
                parentNode == null ? "" : "node " + parentNode);
    }

    public String run(String task) throws Exception {
        goal = task;
        if (mem != null) mem.episode(0, "goal", task);
        convo.add(Message.user(task));
        String root = graph.add(parentNode, NodeKind.AGENT, label(task));
        graph.state(root, NodeState.RUNNING);
        for (int step = 1; step <= maxSteps; step++) {
            steps = step;
            obs.onStep(step);
            if (mem != null) convo.set(0, Message.system(assembleSystem()));
            String node = graph.add(root, NodeKind.AGENT, "step " + step);
            graph.state(node, NodeState.RUNNING);
            maybeCompress(node);

            Router.Tier tier = null;
            ChatClient cl = router.of(Router.Tier.CHEAP);
            if (auction != null) {
                tier = pick(auctionValue(), estimate());
                cl = router.of(tier);
            }

            Turn t;
            try {
                t = cl.stream(convo, tools.schemas(), d -> {
                    if (d instanceof Delta.Text x) onText.accept(x.s());
                });
            } catch (Exception e) {
                if (governor != null) governor.fail();
                if (auction != null && tier != null) auction.settle(tier, false);
                graph.fail(node, e.getMessage());
                graph.fail(root, e.getMessage());
                throw e;
            }
            if (governor != null) governor.ok(t.inTokens() + t.outTokens());
            if (auction != null && tier != null) auction.settle(tier, true);

            inTokens += t.inTokens();
            outTokens += t.outTokens();
            graph.tokens(t.inTokens(), t.outTokens());
            if (budget != null) budget.charge(t.inTokens(), t.outTokens());
            convo.add(Message.assistant(t.content(), t.toolCalls()));

            if (mem != null) mem.episode(step, "assistant", t.content());

            if (!t.hasTools()) {
                graph.finish(node, summarize(t.content()));
                graph.finish(root, "done");
                return t.content();
            }
            runTools(node, t.toolCalls());
            graph.finish(node, t.toolCalls().size() + " tool call(s)");
        }
        graph.fail(root, "max steps");
        return null;
    }

    private void maybeCompress(String node) throws Exception {
        if (!compress || convo.size() <= 3) return;
        long before = Tokens.estimate(convo);
        String digest = Compressor.digest(convo, mem == null ? "" : mem.digest());
        long after = Tokens.estimate(digest);
        long perRound = Math.max(1, before / Math.max(1, steps));
        long remaining = budget == null ? before * 4 : budget.remaining();
        int rounds = Compressor.roundsLeft(remaining, perRound);
        Compressor.Decision d = Compressor.decide(before, compressThreshold, rounds, before - after, after);
        if (!d.go()) return;
        for (Message m : convo) if ("tool".equals(m.role())) foldedTotal++;
        List<Message> keep = new ArrayList<>();
        keep.add(Message.system(mem == null ? stable : assembleSystem()));
        keep.add(Message.user(goal));
        keep.add(Message.user("[compressed context] folded=" + foldedTotal + "\n" + digest));
        convo.clear();
        convo.addAll(keep);
        String id = graph.add(node, NodeKind.COMPRESS, "compress " + before + "->" + after + " tok");
        graph.finish(id, d.reason());
        if (mem != null) {
            mem.summarize(digest);
            mem.episode(steps, "compress", before + " -> " + after + " tok");
        }
        obs.onCompress(steps, before, after, d.reason());
    }

    private Router.Tier pick(double value, long kTokens) {
        Auction.Award a = auction.award(value, kTokens);
        obs.onBid(steps, a);
        if (budget != null) budget.credit(a.profit());
        return a.tier();
    }

    private double auctionValue() {
        double v = governor == null ? 1.0 : governor.valueScale();
        double pressure = budget == null ? 0.0 : budget.usedFraction();
        return v * (1.0 - 0.6 * pressure);
    }

    private long estimate() {
        return Math.max(1, Tokens.estimate(convo) / 1000);
    }

    private void runTools(String parent, List<ToolCall> calls) throws Exception {
        List<Future<String>> futures = new ArrayList<>(calls.size());
        for (ToolCall tc : calls) {
            obs.onToolCall(steps, tc);
            String id = graph.add(parent, NodeKind.TOOL, tc.name());
            String write = writePath(tc);
            futures.add(sched.submit(id, () -> {
                Admission adm = sched.admission();
                if (write == null) return tools.call(tc.name(), tc.args());
                adm.lockPath(write);
                try {
                    return tools.call(tc.name(), tc.args());
                } finally {
                    adm.unlockPath(write);
                }
            }));
        }
        for (int i = 0; i < calls.size(); i++) {
            ToolCall tc = calls.get(i);
            String result;
            try {
                result = futures.get(i).get();
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                result = "error: " + (c == null ? e.getMessage() : c.toString());
            }
            obs.onToolResult(steps, tc, result);
            if (mem != null) mem.episode(steps, "tool:" + tc.name(), result);
            convo.add(Message.tool(tc.id(), result));
        }
    }

    private static String writePath(ToolCall tc) {
        String n = tc.name();
        if (!"write_file".equals(n) && !"edit_file".equals(n)) return null;
        try {
            return Json.parse(tc.args()).at("path").str(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String label(String task) {
        String s = task.replace('\n', ' ').trim();
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static String summarize(String s) {
        if (s == null) return "";
        String one = s.replace('\n', ' ').trim();
        return one.length() <= 60 ? one : one.substring(0, 60) + "…";
    }

    public List<Message> convo() { return convo; }

    public int steps() { return steps; }

    public long inTokens() { return inTokens; }

    public long outTokens() { return outTokens; }
}
