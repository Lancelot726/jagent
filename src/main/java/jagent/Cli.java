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
import jagent.json.Json;
import jagent.mem.Memory;
import jagent.mem.WorkDir;
import jagent.llm.ChatClient;
import jagent.llm.FallbackClient;
import jagent.llm.Message;
import jagent.llm.MockClient;
import jagent.llm.OpenAiClient;
import jagent.llm.Router;
import jagent.llm.ToolCall;
import jagent.term.Ansi;
import jagent.term.Dashboard;
import jagent.term.Lang;
import jagent.term.Term;
import jagent.term.Text;
import jagent.tool.HumanTool;
import jagent.tool.MemoryTool;
import jagent.tool.Tool;
import jagent.tool.ToolRegistry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        Config cfg = Config.load(opt);
        Lang L = cfg.lang;
        try {
            if (pos.isEmpty()) return repl(cfg);
            String cmd = pos.get(0);
            return switch (cmd) {
                case "doctor" -> doctor(cfg);
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

    private static ChatClient client(Config cfg, String baseUrl, String apiKey, String model) {
        return cfg.isMock()
                ? new MockClient(model)
                : new OpenAiClient(baseUrl, apiKey, model, 120);
    }

    private static int run(Config cfg, List<String> rest) throws Exception {
        return run(cfg, rest, new ArrayList<>());
    }

    private static int run(Config cfg, List<String> rest, List<Message> transcript) throws Exception {
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
                cfg.systemPrompt(), factory, cfg.maxSteps));
        tools.add(HumanTool.create(graph));

        Dashboard dash = new Dashboard(Term.out, graph, L, cfg.graph && Term.tty());
        dash.attach(bus);

        Map<String, Integer> toolCount = new LinkedHashMap<>();

        Agent agent = new Agent(router, tools, graph, sched, cfg.systemPrompt(), null,
                new Agent.Observer() {
                    public void onStep(int step) {
                        dash.log("");
                        dash.log(Ansi.GRAY + "  " + L.t("run.step") + " " + step + Ansi.RESET);
                    }

                    public void onToolCall(int step, ToolCall c) {
                        toolCount.merge(c.name(), 1, Integer::sum);
                        dash.log(Ansi.CYAN + "  " + L.t("run.tool") + " " + c.name() + Ansi.RESET
                                + " " + Ansi.GRAY + argBrief(c.args()) + Ansi.RESET);
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
                        if (!cfg.scorecard) return;
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
                }, cfg.maxSteps, auction, budget, gov, null)
                .memory(mem)
                .prior(transcript)
                .compression(true)
                .compressThreshold(cfg.compressThreshold);

        dash.log(Ansi.GRAY + "[" + router.model(Router.Tier.CHEAP) + " | "
                + router.model(Router.Tier.STRONG) + "]" + Ansi.RESET);
        long t0 = System.nanoTime();
        String answer = null;
        String failure = null;
        try {
            answer = agent.run(task);
        } catch (Exception e) {
            failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }

        String outcome = failure != null ? "failed" : (answer == null ? "limit" : "done");
        try {
            dash.log("");
            dash.log(Ansi.GREEN + "  " + L.t("run.answer") + Ansi.RESET);
            if (failure != null) {
                dash.log("    " + L.t("run.failed", agent.steps(), shortWhy(failure)));
                dash.log("    " + L.t("run.hint", hint(L, failure)));
            } else if (answer == null || answer.isBlank()) {
                dash.log("    " + L.t("run.limit", agent.steps()));
            } else {
                for (String line : Text.plain(answer).split("\n", -1)) dash.log(line);
            }

            long ms = (System.nanoTime() - t0) / 1_000_000;
            dash.log("");
            dash.log(Ansi.GRAY + "  [" + L.t("run.steps") + "=" + agent.steps()
                    + " nodes=" + graph.size() + " in=" + agent.inTokens()
                    + " out=" + agent.outTokens() + " " + ms + "ms]" + Ansi.RESET);
            if (cfg.scorecard) {
                dash.log(Ansi.GRAY + "  [" + L.t("run.budget") + " " + budget.spent() + "/" + budget.cap()
                        + " tok " + L.t("run.credits") + " " + String.format("%.2f", budget.credits())
                        + " conc=" + gov.permits() + " err=" + String.format("%.0f%%", gov.errorRate() * 100)
                        + (gov.lastAction().isEmpty() ? "" : " ctl:" + gov.lastAction())
                        + "]" + Ansi.RESET);
            }
            try {
                graph.savePlan(work.path(WorkDir.PLAN), task);
                dash.log(Ansi.GRAY + "  " + L.t("run.plan", work.path(WorkDir.PLAN).toString())
                        + Ansi.RESET);
            } catch (Exception ignore) {
            }
            if (cfg.scorecard) {
                dash.log("");
                for (String line : score.lines()) dash.log(Ansi.BLUE + "  " + line + Ansi.RESET);
            }
            try {
                score.save(work.path(WorkDir.SCORECARD));
                if (cfg.scorecard) {
                    dash.log(Ansi.GRAY + "  " + L.t("score.saved", work.path(WorkDir.SCORECARD).toString())
                            + Ansi.RESET);
                }
            } catch (Exception ignore) {
            }

            try {
                mem.recordRun(recordOf(task, toolCount, agent.steps(), outcome, failure));
            } catch (Exception ignore) {
            }
        } finally {
            sched.close();
            dash.close();
            bus.close();
        }

        if (failure == null && answer != null && !answer.isBlank()) {
            transcript.add(Message.user(task));
            transcript.add(Message.assistant(answer, List.of()));
        }
        return failure == null ? 0 : 1;
    }

    private static String argBrief(String args) {
        String s = args == null ? "" : args.trim();
        try {
            Json j = Json.parse(s);
            for (String k : new String[]{"path", "command", "task", "pattern", "name", "url"}) {
                String v = j.at(k).str("");
                if (!v.isBlank()) return oneLine(v);
            }
        } catch (Exception ignore) {
        }
        return oneLine(s);
    }

    private static String oneLine(String s) {
        String one = s.replace('\n', ' ').replace('\r', ' ').trim();
        return one.length() <= 60 ? one : one.substring(0, 60) + "…";
    }

    private static String hint(Lang L, String failure) {
        String m = failure == null ? "" : failure.toLowerCase();
        if (m.contains("429") || m.contains("rate limit") || m.contains("too many")) return L.t("run.hint.rate");
        if (m.contains("timeout") || m.contains("timed out")) return L.t("run.hint.hang");
        if (m.contains("401") || m.contains("403") || m.contains("unauthorized") || m.contains("api key"))
            return L.t("run.hint.auth");
        return L.t("run.hint.other");
    }

    private static String recordOf(String task, Map<String, Integer> tools, int steps,
                                   String outcome, String failure) {
        StringBuilder use = new StringBuilder();
        for (Map.Entry<String, Integer> e : tools.entrySet()) {
            if (use.length() > 0) use.append(',');
            use.append(e.getKey()).append(':').append(e.getValue());
        }
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String goal = task.replace('\n', ' ').trim();
        if (goal.length() > 80) goal = goal.substring(0, 80) + "…";
        String res = outcome + (failure == null ? "" : "(" + shortWhy(failure) + ")");
        return ts + " goal=" + goal + " tools=" + (use.length() == 0 ? "none" : use)
                + " steps=" + steps + " result=" + res;
    }

    private static String shortWhy(String failure) {
        String s = failure.replace('\n', ' ').trim();
        int cut = s.indexOf(": http");
        if (cut > 0) s = s.substring(0, cut);
        return s.length() <= 60 ? s : s.substring(0, 60) + "…";
    }

    private static int repl(Config cfg) throws Exception {
        Lang L = cfg.lang;
        Term.out.println(Config.APP + " " + Config.VERSION + " · " + L.t("app.tag"));
        Term.out.println(Ansi.GRAY + L.t("repl.hint") + Ansi.RESET);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        List<Message> transcript = new ArrayList<>();
        while (true) {
            Term.out.println();
            Term.out.print(Ansi.GREEN + "> " + Ansi.RESET);
            Term.out.flush();
            String line = in.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("exit") || line.equals("quit") || line.equals(":q")) break;
            if (line.equals(":help") || line.equals("help")) { help(L); continue; }
            if (line.equals(":doctor") || line.equals("doctor")) { doctor(cfg); continue; }
            if (line.startsWith(":")) {
                Term.err.println(L.t("err.unknown", line));
                continue;
            }
            try {
                run(cfg, List.of(line), transcript);
            } catch (Exception e) {
                Term.err.println(L.t("err.prefix", String.valueOf(e.getMessage())));
            }
        }
        Term.out.println(Ansi.GRAY + L.t("repl.bye") + Ansi.RESET);
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
        Term.out.println("  run <task>        " + L.t("cmd.run"));
        Term.out.println("  version           " + L.t("cmd.version"));
        Term.out.println("  help              " + L.t("cmd.help"));
        Term.out.println("  (no args)         " + L.t("cmd.repl"));
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
        Term.out.println("  --max-steps <n>           " + L.t("opt.maxsteps"));
        Term.out.println("  --tool-delay <ms>         " + L.t("opt.tooldelay"));
        Term.out.println("  --no-graph                " + L.t("opt.nograph"));
        Term.out.println("  --scorecard               " + L.t("opt.scorecard"));
        return 0;
    }
}
