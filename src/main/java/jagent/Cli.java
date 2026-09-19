package jagent;

import jagent.agent.Agent;
import jagent.agent.Auction;
import jagent.agent.Budget;
import jagent.agent.Governor;
import jagent.agent.Scorecard;
import jagent.agent.SpawnTool;
import jagent.core.Bus;
import jagent.graph.Admission;
import jagent.graph.Graph;
import jagent.graph.Scheduler;
import jagent.mem.Memory;
import jagent.mem.WorkDir;
import jagent.llm.ChatClient;
import jagent.llm.Delta;
import jagent.llm.FallbackClient;
import jagent.llm.Message;
import jagent.llm.MockClient;
import jagent.llm.OpenAiClient;
import jagent.llm.Router;
import jagent.llm.ToolCall;
import jagent.llm.Turn;
import jagent.term.Ansi;
import jagent.term.Dashboard;
import jagent.term.Lang;
import jagent.term.Term;
import jagent.tool.HumanTool;
import jagent.tool.MemoryTool;
import jagent.tool.Tool;
import jagent.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class Cli {

    private Cli() {}

    public static int run(String[] argv) {
        Map<String, String> opt = new LinkedHashMap<>();
        List<String> pos = new ArrayList<>();
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq > 1) {
                    opt.put(a.substring(2, eq), a.substring(eq + 1));
                } else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                    opt.put(a.substring(2), argv[++i]);
                } else {
                    opt.put(a.substring(2), "true");
                }
            } else {
                pos.add(a);
            }
        }
        String cmd = pos.isEmpty() ? "help" : pos.get(0);
        Config cfg = Config.load(opt);
        Lang L = cfg.lang;
        try {
            return switch (cmd) {
                case "doctor" -> doctor(cfg);
                case "chat" -> chat(cfg, pos.subList(1, pos.size()));
                case "run" -> run(cfg, pos.subList(1, pos.size()));
                case "version" -> { Term.out.println(Config.APP + " " + Config.VERSION); yield 0; }
                case "help", "-h" -> help(L);
                default -> {
                    Term.err.println(L.t("err.unknown", cmd));
                    help(L);
                    yield 2;
                }
            };
        } catch (Exception e) {
            Term.err.println(L.t("err.prefix", String.valueOf(e.getMessage())));
            return 1;
        }
    }

    private static boolean badCfg(Config cfg, Lang L) {
        if (cfg.isMock()) return false;
        StringBuilder miss = new StringBuilder();
        add(miss, cfg.baseUrl, "base-url");
        add(miss, cfg.apiKey, "api-key");
        if (miss.length() == 0) return false;
        Term.err.println(L.t("err.nocfg", miss));
        return true;
    }

    private static void add(StringBuilder sb, String v, String name) {
        if (v != null && !v.isBlank()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(name);
    }

    private static ChatClient client(Config cfg) {
        return client(cfg, cfg.baseUrl, cfg.apiKey, cfg.model);
    }

    private static ChatClient client(Config cfg, String model) {
        return client(cfg, cfg.baseUrl, cfg.apiKey, model);
    }

    private static ChatClient client(Config cfg, String baseUrl, String apiKey, String model) {
        return cfg.isMock()
                ? new MockClient(model)
                : new OpenAiClient(baseUrl, apiKey, model, 120);
    }

    private static int chat(Config cfg, List<String> rest) throws Exception {
        String prompt = String.join(" ", rest).trim();
        if (prompt.isEmpty()) {
            Term.err.println("chat: " + cfg.lang.t("opt.lang"));
            return 2;
        }
        if (badCfg(cfg, cfg.lang)) return 2;
        ChatClient cl = client(cfg);
        List<Message> msgs = new ArrayList<>();
        msgs.add(Message.system(cfg.systemPrompt()));
        msgs.add(Message.user(prompt));

        Term.out.println(Ansi.GRAY + "[" + cl.model() + "]" + Ansi.RESET);
        long t0 = System.nanoTime();
        Turn t = cl.stream(msgs, List.of(), d -> {
            switch (d) {
                case Delta.Text x -> Term.out.print(x.s());
                case Delta.Reasoning r -> Term.out.print(Ansi.GRAY + r.s() + Ansi.RESET);
                case Delta.ToolStart ts -> {
                    Term.out.println();
                    Term.out.println(Ansi.CYAN + "  tool# " + ts.index() + " " + ts.name() + Ansi.RESET);
                }
                case Delta.Done ignored -> { }
            }
        });
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Term.out.println();
        for (ToolCall tc : t.toolCalls()) {
            Term.out.println(Ansi.CYAN + "  args  " + tc.name() + " " + tc.args() + Ansi.RESET);
        }
        Term.out.println(Ansi.GRAY + "  [" + t.finishReason() + " in=" + t.inTokens()
                + " out=" + t.outTokens() + " tools=" + t.toolCalls().size() + " " + ms + "ms]" + Ansi.RESET);
        return 0;
    }

    private static int run(Config cfg, List<String> rest) throws Exception {
        Lang L = cfg.lang;
        WorkDir work = new WorkDir(cfg.cwd);
        String task = String.join(" ", rest).trim();
        if (task.isEmpty() && cfg.resume) task = Graph.planGoal(work.path(WorkDir.PLAN));
        if (task.isEmpty()) {
            Term.err.println(L.t("run.usage"));
            return 2;
        }
        if (badCfg(cfg, L)) return 2;
        ChatClient cheap = client(cfg);
        ChatClient strong = cfg.hasStrong()
                ? client(cfg, cfg.strongBaseUrl(), cfg.strongApiKey(), cfg.modelStrong)
                : cheap;

        Bus bus = new Bus();
        Graph graph = new Graph(bus);
        Admission adm = new Admission(cfg.permits);
        Governor gov = new Governor(adm);
        Scheduler sched = new Scheduler(graph, adm);
        sched.latencyMs(cfg.toolDelayMs);
        Auction auction = new Auction();
        Budget budget = new Budget(cfg.budget);
        FallbackClient cheapFb = new FallbackClient(cheap, strong, gov, 2);
        FallbackClient strongFb = new FallbackClient(strong, cheap, gov, 2);
        Router router = new Router(cheapFb, strongFb);
        Scorecard score = new Scorecard(auction, budget, gov, adm, graph, cheapFb, strongFb, L);

        Memory mem = new Memory(work);

        BiFunction<String, String, ToolRegistry> factory = (lbl, sub) -> {
            ToolRegistry r = ToolRegistry.standard(cfg.cwd);
            for (Tool t : MemoryTool.all(mem)) r.add(t);
            return r;
        };
        ToolRegistry tools = factory.apply("root", task);
        tools.add(SpawnTool.create(cfg.cwd, router, graph, sched, auction, budget, gov,
                cfg.systemPrompt(), factory));
        tools.add(HumanTool.create(graph));

        Dashboard dash = new Dashboard(Term.out, graph, L, cfg.graph && Term.tty());
        dash.attach(bus);

        Agent agent = new Agent(router, tools, graph, sched, cfg.systemPrompt(), dash::stream,
                new Agent.Observer() {
                    public void onStep(int step) {
                        dash.log("");
                        dash.log(Ansi.GRAY + "  " + L.t("run.step") + " " + step + Ansi.RESET);
                    }

                    public void onToolCall(int step, ToolCall c) {
                        dash.log(Ansi.CYAN + "  " + L.t("run.tool") + " " + c.name() + Ansi.RESET
                                + " " + Ansi.GRAY + c.args() + Ansi.RESET);
                    }

                    public void onToolResult(int step, ToolCall c, String result) {
                        String one = result.replace('\n', ' ');
                        if (one.length() > 160) one = one.substring(0, 160) + "...";
                        dash.log(Ansi.GRAY + "  " + L.t("run.result") + " " + one + Ansi.RESET);
                    }

                    public void onCompress(int step, long before, long after, String reason) {
                        score.onCompress(before, after);
                        dash.log(Ansi.YELLOW + "  " + L.t("run.compress", before, after) + Ansi.RESET
                                + " " + Ansi.GRAY + reason + Ansi.RESET);
                    }

                    public void onBid(int step, Auction.Award a) {
                        StringBuilder sb = new StringBuilder(Ansi.MAGENTA + "  " + L.t("run.bid"));
                        for (Auction.Bid b : a.bids()) {
                            sb.append(' ').append(b.tier().name().toLowerCase())
                                    .append('=').append(String.format("%.2f", b.utility()));
                        }
                        sb.append(" -> ").append(a.tier().name().toLowerCase())
                                .append(" (pay ").append(String.format("%.2f", a.pay()))
                                .append(", ").append(String.format("%+.2f", a.profit())).append(')')
                                .append(Ansi.RESET);
                        dash.log(sb.toString());
                    }
                }, 16, auction, budget, gov, null)
                .memory(mem)
                .compression(true)
                .compressThreshold(cfg.compressThreshold);

        dash.log(Ansi.GRAY + "[" + router.model(Router.Tier.CHEAP) + " | "
                + router.model(Router.Tier.STRONG) + "]" + Ansi.RESET);
        long t0 = System.nanoTime();
        String answer = null;
        boolean threw = false;
        try {
            answer = agent.run(task);
        } catch (Exception e) {
            threw = true;
            String m = e.getMessage();
            dash.log(Ansi.RED + "  " + L.t("run.abort", m == null ? e.getClass().getSimpleName() : m)
                    + Ansi.RESET);
            throw e;
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            dash.log("");
            if (!threw && answer == null) dash.log(Ansi.YELLOW + "  " + L.t("run.limit", agent.steps()) + Ansi.RESET);
            dash.log(Ansi.GRAY + "  [" + L.t("run.steps") + "=" + agent.steps()
                    + " nodes=" + graph.size() + " in=" + agent.inTokens()
                    + " out=" + agent.outTokens() + " " + ms + "ms]" + Ansi.RESET);
            dash.log(Ansi.GRAY + "  [" + L.t("run.budget") + " " + budget.spent() + "/" + budget.cap()
                    + " tok " + L.t("run.credits") + " " + String.format("%.2f", budget.credits())
                    + " conc=" + gov.permits() + " err=" + String.format("%.0f%%", gov.errorRate() * 100)
                    + (gov.lastAction().isEmpty() ? "" : " ctl:" + gov.lastAction())
                    + "]" + Ansi.RESET);
            try {
                graph.savePlan(work.path(WorkDir.PLAN), task);
                dash.log(Ansi.GRAY + "  " + L.t("run.plan", work.path(WorkDir.PLAN).toString())
                        + Ansi.RESET);
            } catch (Exception ignore) {
            }
            dash.log("");
            for (String line : score.lines()) dash.log(Ansi.BLUE + "  " + line + Ansi.RESET);
            try {
                score.save(work.path(WorkDir.SCORECARD));
                dash.log(Ansi.GRAY + "  " + L.t("score.saved", work.path(WorkDir.SCORECARD).toString())
                        + Ansi.RESET);
            } catch (Exception ignore) {
            }
            sched.close();
            dash.close();
            bus.close();
        }
        return 0;
    }

    private static void row(String k, String v) {
        int pad = 14 - Ansi.width(k);
        Term.out.println("  " + k + " ".repeat(Math.max(1, pad)) + v);
    }

    private static int doctor(Config cfg) {
        Lang L = cfg.lang;
        Term.out.println(Config.APP + " " + Config.VERSION + " · doctor");
        row(L.t("col.java"), System.getProperty("java.version") + "  (" + System.getProperty("java.vendor") + ")");
        row(L.t("col.os"), System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " / " + System.getProperty("os.arch"));
        row(L.t("col.cwd"), cfg.cwd.toString());
        row(L.t("col.stdout"), Term.stdoutEncoding());
        row(L.t("col.console"), Term.consoleEncoding());
        row(L.t("col.locale"), System.getProperty("user.language") + "_" + System.getProperty("user.country"));
        row(L.t("col.size"), cfg.width + "x" + cfg.height + "  " + L.t("size.note"));
        row(L.t("col.provider"), cfg.provider + (cfg.isMock() ? "  " + L.t("mock.note") : ""));
        row(L.t("col.userlang"), L.code() + " / " + L.tag());
        if (!cfg.isMock()) {
            row(L.t("col.tier.cheap"), cfg.baseUrl.isBlank()
                    ? L.t("key.unset") : cfg.baseUrl + "  " + cfg.model);
            row(L.t("col.key.cheap"), cfg.apiKey.isEmpty()
                    ? L.t("key.unset") : L.t("key.set", cfg.apiKey.length()));
            row(L.t("col.tier.strong"), cfg.hasStrong()
                    ? cfg.strongBaseUrl() + "  " + cfg.modelStrong
                    : L.t("tier.same"));
            row(L.t("col.key.strong"), cfg.hasStrong()
                    ? (cfg.strongApiKey().isEmpty()
                            ? L.t("key.unset") : L.t("key.set", cfg.strongApiKey().length()))
                    : L.t("tier.same"));
            if (cfg.hasStrong() && cfg.strongBaseUrl().equals(cfg.baseUrl)) {
                Term.out.println(Ansi.YELLOW + "  " + L.t("tier.sameSrc") + Ansi.RESET);
            }
        }
        Term.out.println();
        Term.out.print("  " + L.t("probe.utf8") + "  ");
        Term.out.println("├─ └─ │ ● ◐ ○ ✗ ✓ →  " + L.t("probe.chars"));
        Term.out.print("  " + L.t("width.check") + "  ");
        String probe = "状态▸○◐●";
        Term.out.println(probe + "  " + L.t("width.note", Ansi.width(probe), probe.length()));
        if (!Term.stdoutEncoding().toUpperCase().contains("UTF")) {
            Term.out.println();
            Term.out.println(Ansi.YELLOW + "  " + L.t("warn.enc") + Ansi.RESET);
            Term.out.println(Ansi.GRAY + "  " + L.t("warn.chcp") + Ansi.RESET);
        }
        return 0;
    }

    private static int help(Lang L) {
        Term.out.println(Config.APP + " " + Config.VERSION + " · " + L.t("app.tag"));
        Term.out.println();
        Term.out.println(L.t("usage"));
        Term.out.println();
        Term.out.println(L.t("commands"));
        Term.out.println("  doctor            " + L.t("cmd.doctor"));
        Term.out.println("  chat <prompt>     " + L.t("cmd.chat"));
        Term.out.println("  run <task>        " + L.t("cmd.run"));
        Term.out.println("  version           " + L.t("cmd.version"));
        Term.out.println("  help              " + L.t("cmd.help"));
        Term.out.println();
        Term.out.println(L.t("options"));
        Term.out.println("  --provider <mock|openai>  " + L.t("opt.provider"));
        Term.out.println("  --base-url <url>          " + L.t("opt.baseurl"));
        Term.out.println("  --api-key <key>           " + L.t("opt.apikey"));
        Term.out.println("  --model <name>            " + L.t("opt.model"));
        Term.out.println("  --model-strong <name>     " + L.t("opt.modelstrong"));
        Term.out.println("  --base-url-strong <url>   " + L.t("opt.baseurlstrong"));
        Term.out.println("  --api-key-strong <key>    " + L.t("opt.apikeystrong"));
        Term.out.println("  --budget <tokens>         " + L.t("opt.budget"));
        Term.out.println("  --compress-after <tokens> " + L.t("opt.compress"));
        Term.out.println("  --resume                  " + L.t("opt.resume"));
        Term.out.println("  --width / --height <n>    " + L.t("opt.size"));
        Term.out.println("  --cwd <dir>               " + L.t("opt.cwd"));
        Term.out.println("  --user-lang <zh|eng>      " + L.t("opt.lang"));
        Term.out.println("  --permits <n>             " + L.t("opt.permits"));
        Term.out.println("  --tool-delay <ms>         " + L.t("opt.tooldelay"));
        Term.out.println("  --no-graph                " + L.t("opt.nograph"));
        return 0;
    }
}
