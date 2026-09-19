package jagent.term;

import jagent.core.Bus;
import jagent.core.Event;
import jagent.graph.Graph;
import jagent.graph.Snapshot;

import java.io.PrintStream;
import java.util.List;

public final class Dashboard implements AutoCloseable {

    private final PrintStream out;
    private final Graph graph;
    private final Lang lang;
    private final boolean live;
    private int drawn;
    private boolean closed;

    public Dashboard(PrintStream out, Graph graph, Lang lang, boolean live) {
        this.out = out;
        this.graph = graph;
        this.lang = lang;
        this.live = live;
    }

    public void attach(Bus bus) {
        bus.onEvent(e -> {
            if (e.kind() == Event.Kind.LOG) log(e.text());
            else draw();
        });
    }

    public synchronized void log(String line) {
        if (closed) return;
        erase();
        out.println(line);
        out.flush();
        draw();
    }

    public synchronized void stream(String chunk) {
        if (closed || chunk.isEmpty()) return;
        if (!live) {
            out.print(chunk);
            out.flush();
            return;
        }
        erase();
        out.print(chunk);
        out.flush();
    }

    public synchronized void draw() {
        if (closed || !live) return;
        Snapshot s = graph.snapshot();
        erase();
        List<String> lines = GraphView.render(s, lang);
        for (String l : lines) out.println(l);
        out.flush();
        drawn = lines.size();
    }

    private void erase() {
        if (drawn <= 0) return;
        out.print(Ansi.up(drawn) + Ansi.clearDown());
        out.flush();
        drawn = 0;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        draw();
        closed = true;
        drawn = 0;
    }
}
