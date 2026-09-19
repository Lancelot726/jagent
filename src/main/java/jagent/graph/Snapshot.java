package jagent.graph;

import java.util.List;

public record Snapshot(List<Row> rows, int running, int done, int failed,
                       long inTokens, long outTokens) {

    public record Row(String id, NodeKind kind, String label, NodeState state,
                      long ms, String note, int depth, boolean last) {}

    public int size() { return rows.size(); }

    public static Snapshot empty() {
        return new Snapshot(List.of(), 0, 0, 0, 0, 0);
    }
}
