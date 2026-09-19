package jagent.core;

public record Event(Kind kind, String node, String text) {

    public enum Kind { NODE_ADD, NODE_STATE, TOKEN, BUDGET, LOG }

    public static Event nodeAdd(String id, String label) {
        return new Event(Kind.NODE_ADD, id, label);
    }

    public static Event nodeState(String id, String state) {
        return new Event(Kind.NODE_STATE, id, state);
    }

    public static Event log(String text) {
        return new Event(Kind.LOG, null, text);
    }
}
