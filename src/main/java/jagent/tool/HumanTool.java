package jagent.tool;

import jagent.graph.Graph;
import jagent.graph.NodeKind;
import jagent.graph.NodeState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class HumanTool {

    private HumanTool() {}

    public static Tool create(Graph graph) {
        return new Tool("ask_human", "Ask the human operator a question and wait for the answer. Creates a HUMAN node on the graph.",
                a -> {
                    String q = a.at("question").str(null);
                    if (q == null || q.isBlank()) throw new IllegalArgumentException("question is required");
                    String node = graph.add(jagent.core.RunCtx.current(), NodeKind.HUMAN, q);
                    graph.state(node, NodeState.RUNNING);
                    if (System.console() == null) {
                        graph.finish(node, "auto-approved (no tty)");
                        return "auto-approved: no interactive console available, proceed with your best judgement.";
                    }
                    System.out.println();
                    System.out.println("  [human] " + q);
                    System.out.print("  > ");
                    System.out.flush();
                    BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    String line = r.readLine();
                    graph.finish(node, line == null ? "(no answer)" : line);
                    return line == null || line.isBlank() ? "(no answer, proceed)" : line;
                })
                .req("question", "string", "The question to put to the human.");
    }
}
