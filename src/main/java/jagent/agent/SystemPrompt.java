package jagent.agent;

public final class SystemPrompt {

    private SystemPrompt() {}

    public static String assemble(String stable, String goal, String memory, String node) {
        StringBuilder sb = new StringBuilder(stable == null ? "" : stable);
        section(sb, "goal", goal);
        section(sb, "memory", memory);
        section(sb, "node", node);
        return sb.toString();
    }

    public static String prefix(String stable) {
        return stable == null ? "" : stable;
    }

    private static void section(StringBuilder sb, String tag, String body) {
        if (body == null || body.isBlank()) return;
        sb.append("\n\n[").append(tag).append("]\n").append(body.strip());
    }
}
