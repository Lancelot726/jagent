package jagent.agent;

import jagent.core.RunCtx;
import jagent.graph.Graph;
import jagent.graph.NodeKind;
import jagent.graph.NodeState;
import jagent.graph.Scheduler;
import jagent.llm.Router;
import jagent.tool.Tool;
import jagent.tool.ToolRegistry;

import java.nio.file.Path;
import java.util.function.BiFunction;

public final class SpawnTool {

    private SpawnTool() {}

    public static final int MAX_DEPTH = 2;

    public static Tool create(Path root, Router router, Graph graph, Scheduler sched,
                              Auction auction, Budget budget, Governor governor, String system,
                              BiFunction<String, String, ToolRegistry> registryFactory, int maxSteps) {
        return create(root, router, graph, sched, auction, budget, governor, system,
                registryFactory, maxSteps, 1);
    }

    private static Tool create(Path root, Router router, Graph graph, Scheduler sched,
                               Auction auction, Budget budget, Governor governor, String system,
                               BiFunction<String, String, ToolRegistry> registryFactory,
                               int maxSteps, int depth) {
        return new Tool("spawn_agent",
                "Spawn a sub-agent on its own graph node to work on one independent sub-task. "
                        + "Call twice with different sub-tasks to run them in parallel.",
                args -> {
                    String task = args.at("task").str(null);
                    if (task == null || task.isBlank()) throw new IllegalArgumentException("task is required");
                    String label = args.at("label").str("sub");

                    String node = graph.add(parentNode(), NodeKind.AGENT, label + ": " + oneLine(task));
                    ToolRegistry sub = registryFactory.apply(label, task);
                    if (depth < MAX_DEPTH) {
                        sub.add(create(root, router, graph, sched, auction, budget, governor, system,
                                registryFactory, maxSteps, depth + 1));
                    }
                    Agent a = new Agent(router, sub, graph, sched, system, s -> {}, null, maxSteps,
                            auction, budget, governor, node);
                    try {
                        String out = a.run(task);
                        graph.state(node, NodeState.DONE);
                        return out == null ? "(sub-agent hit its step limit)" : out;
                    } catch (Exception e) {
                        graph.fail(node, e.getMessage());
                        return "error: sub-agent failed: " + e.getMessage();
                    }
                })
                .req("task", "string", "The sub-task for the child agent.")
                .opt("label", "string", "Short label shown on the graph.");
    }

    private static String parentNode() {
        return RunCtx.current();
    }

    private static String oneLine(String s) {
        String one = s.replace('\n', ' ').trim();
        return one.length() <= 32 ? one : one.substring(0, 32) + "…";
    }
}
