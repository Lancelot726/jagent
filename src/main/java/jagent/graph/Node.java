package jagent.graph;

public final class Node {

    public final String id;
    public final String parent;
    public final NodeKind kind;
    public final String label;
    public final long bornNs = System.nanoTime();

    volatile NodeState state = NodeState.PENDING;
    volatile long startNs;
    volatile long endNs;
    volatile String note = "";
    volatile long costTokens;

    Node(String id, String parent, NodeKind kind, String label) {
        this.id = id;
        this.parent = parent;
        this.kind = kind;
        this.label = label;
    }

    public NodeState state() { return state; }

    public long millis() {
        long s = startNs;
        if (s == 0) return 0;
        long e = endNs == 0 ? System.nanoTime() : endNs;
        return (e - s) / 1_000_000;
    }
}
