package jagent.graph;

import jagent.core.Bus;
import jagent.core.Event;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Graph {

    private final Map<String, Node> byId = new ConcurrentHashMap<>();
    private final Set<String> order = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private final AtomicInteger seq = new AtomicInteger();
    private final AtomicLong inTok = new AtomicLong();
    private final AtomicLong outTok = new AtomicLong();
    private final Bus bus;

    public Graph(Bus bus) {
        this.bus = bus;
    }

    public String add(String parent, NodeKind kind, String label) {
        String id = kind.name().toLowerCase() + "-" + seq.incrementAndGet();
        byId.put(id, new Node(id, parent, kind, label));
        order.add(id);
        bus.emit(Event.nodeAdd(id, label));
        return id;
    }

    public Node get(String id) { return byId.get(id); }

    public void state(String id, NodeState s) {
        Node n = byId.get(id);
        if (n == null) return;
        synchronized (n) {
            if (n.state.terminal()) return;
            n.state = s;
            if (s == NodeState.RUNNING && n.startNs == 0) n.startNs = System.nanoTime();
            if (s.terminal()) n.endNs = System.nanoTime();
        }
        bus.emit(Event.nodeState(id, s.name()));
    }

    public void finish(String id, String note) {
        Node n = byId.get(id);
        if (n == null) return;
        synchronized (n) {
            n.note = note == null ? "" : note;
            n.state = NodeState.DONE;
            n.endNs = System.nanoTime();
        }
        bus.emit(Event.nodeState(id, NodeState.DONE.name()));
    }

    public void fail(String id, String note) {
        Node n = byId.get(id);
        if (n == null) return;
        synchronized (n) {
            n.note = note == null ? "" : note;
            n.state = NodeState.FAILED;
            n.endNs = System.nanoTime();
        }
        bus.emit(Event.nodeState(id, NodeState.FAILED.name()));
    }

    public void tokens(long in, long out) {
        inTok.addAndGet(in);
        outTok.addAndGet(out);
    }

    public int running() {
        int n = 0;
        for (Node x : byId.values()) if (x.state == NodeState.RUNNING) n++;
        return n;
    }

    public int count(NodeState s) {
        int n = 0;
        for (Node x : byId.values()) if (x.state == s) n++;
        return n;
    }

    public int size() { return byId.size(); }

    public String savePlan(java.nio.file.Path file, String goal) throws java.io.IOException {
        Snapshot s = snapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("# plan\n\n");
        sb.append("goal: ").append(goal == null ? "" : goal.replace('\n', ' ')).append('\n');
        sb.append("updated: ").append(java.time.Instant.now()).append('\n');
        sb.append("nodes: ").append(s.size()).append(" done: ").append(s.done())
                .append(" failed: ").append(s.failed()).append('\n');
        sb.append("tokens: in=").append(s.inTokens()).append(" out=").append(s.outTokens()).append('\n');
        sb.append("\n## steps\n\n");
        for (Snapshot.Row r : s.rows()) {
            sb.append("- ").append(r.state().name().toLowerCase()).append("  ")
                    .append(r.kind().name().toLowerCase()).append("  ")
                    .append(r.label()).append(r.note() == null || r.note().isBlank() ? "" : "  | " + r.note())
                    .append('\n');
        }
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
        return sb.toString();
    }

    public static String planGoal(java.nio.file.Path file) throws java.io.IOException {
        if (!java.nio.file.Files.isRegularFile(file)) return "";
        for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            if (line.startsWith("goal: ")) return line.substring(6).trim();
        }
        return "";
    }

    public Snapshot snapshot() {
        List<String> ids;
        synchronized (order) {
            ids = new ArrayList<>(order);
        }
        List<Snapshot.Row> rows = new ArrayList<>();
        List<String> roots = new ArrayList<>();
        for (String id : ids) {
            Node n = byId.get(id);
            if (n != null && n.parent == null) roots.add(id);
        }
        for (int i = 0; i < roots.size(); i++) {
            walk(roots.get(i), 0, i == roots.size() - 1, rows, ids);
        }
        return new Snapshot(rows, count(NodeState.RUNNING), count(NodeState.DONE),
                count(NodeState.FAILED), inTok.get(), outTok.get());
    }

    private void walk(String id, int depth, boolean last, List<Snapshot.Row> rows, List<String> ids) {
        Node n = byId.get(id);
        if (n == null) return;
        rows.add(new Snapshot.Row(n.id, n.kind, n.label, n.state, n.millis(), n.note, depth, last));
        List<String> kids = new ArrayList<>();
        for (String k : ids) {
            Node c = byId.get(k);
            if (c != null && id.equals(c.parent)) kids.add(k);
        }
        for (int i = 0; i < kids.size(); i++) {
            walk(kids.get(i), depth + 1, i == kids.size() - 1, rows, ids);
        }
    }
}
