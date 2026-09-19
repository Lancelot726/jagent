package jagent.term;

import jagent.graph.NodeKind;
import jagent.graph.NodeState;
import jagent.graph.Snapshot;

import java.util.ArrayList;
import java.util.List;

public final class GraphView {

    private GraphView() {}

    private static final int MAX_ROWS = 24;
    private static final int MAX_DEPTH = 6;

    public static List<String> render(Snapshot s, Lang lang) {
        return render(s, lang, true);
    }

    public static List<String> render(Snapshot s, Lang lang, boolean color) {
        List<String> out = new ArrayList<>();
        out.add(c(Ansi.GRAY, color) + lang.t("graph.title") + " " + stat(s, lang) + c(Ansi.RESET, color));
        if (s.rows().isEmpty()) return out;

        boolean[] lastAt = new boolean[MAX_DEPTH + 2];
        int shown = 0;
        for (Snapshot.Row r : s.rows()) {
            if (r.depth() > MAX_DEPTH) continue;
            if (shown++ >= MAX_ROWS) {
                out.add("  " + c(Ansi.GRAY, color) + "… +" + (s.rows().size() - MAX_ROWS) + c(Ansi.RESET, color));
                break;
            }
            lastAt[r.depth()] = r.last();

            StringBuilder b = new StringBuilder("  ");
            for (int d = 1; d < r.depth(); d++) b.append(lastAt[d] ? "   " : "│  ");
            if (r.depth() > 0) b.append(r.last() ? "└─ " : "├─ ");

            b.append(c(color(r.state()), color)).append(r.state().glyph()).append(c(Ansi.RESET, color)).append(' ');
            b.append(c(Ansi.GRAY, color)).append(kind(r.kind())).append(c(Ansi.RESET, color)).append(' ');
            b.append(r.label());
            if (r.ms() > 0) b.append(c(Ansi.GRAY, color)).append("  ").append(r.ms()).append("ms").append(c(Ansi.RESET, color));
            if (!r.note().isEmpty()) b.append(c(Ansi.GRAY, color)).append("  ").append(clip(r.note())).append(c(Ansi.RESET, color));
            out.add(b.toString());
        }
        return out;
    }

    private static String stat(Snapshot s, Lang lang) {
        return lang.t("graph.stat", s.size(), s.running(), s.done(), s.failed())
                + "  " + lang.t("graph.tokens", s.inTokens(), s.outTokens());
    }

    static String color(NodeState st) {
        return switch (st) {
            case RUNNING -> Ansi.CYAN;
            case DONE -> Ansi.GREEN;
            case FAILED -> Ansi.RED;
            case BLOCKED -> Ansi.YELLOW;
            default -> Ansi.GRAY;
        };
    }

    static String kind(NodeKind k) {
        return switch (k) {
            case AGENT -> "agent";
            case TOOL -> "tool ";
            case COMPRESS -> "cmprs";
            case MEM -> "mem  ";
            case GATE -> "gate ";
            case HUMAN -> "human";
        };
    }

    private static String clip(String s) {
        return s.length() <= 48 ? s : s.substring(0, 48) + "…";
    }

    private static String c(String ansi, boolean on) {
        return on ? ansi : "";
    }
}
