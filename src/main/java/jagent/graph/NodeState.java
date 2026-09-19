package jagent.graph;

public enum NodeState {
    PENDING("○"),
    READY("◌"),
    RUNNING("◐"),
    DONE("●"),
    FAILED("✗"),
    BLOCKED("⊘");

    private final String glyph;

    NodeState(String glyph) { this.glyph = glyph; }

    public String glyph() { return glyph; }

    public boolean terminal() { return this == DONE || this == FAILED; }
}
