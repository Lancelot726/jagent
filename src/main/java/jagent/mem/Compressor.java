package jagent.mem;

import jagent.llm.Message;
import jagent.llm.ToolCall;

import java.util.List;

public final class Compressor {

    public record Decision(boolean go, double roi, long benefit, long cost, String reason) {}

    private Compressor() {}

    public static Decision decide(long ctxTokens, long threshold, int roundsLeft,
                                  long savingPerRound, long cost) {
        if (ctxTokens <= threshold) {
            return new Decision(false, 0, 0, cost,
                    "ctx " + ctxTokens + " <= " + threshold);
        }
        if (roundsLeft <= 0) {
            return new Decision(false, 0, 0, cost, "no rounds left");
        }
        long benefit = (long) roundsLeft * Math.max(0, savingPerRound);
        double roi = benefit / (double) Math.max(1, cost);
        boolean go = roi > 1.5;
        return new Decision(go, roi, benefit, cost, String.format(
                "ctx %d > %d, %d rounds x %d saved vs %d cost -> ROI %.1fx",
                ctxTokens, threshold, roundsLeft, savingPerRound, cost, roi));
    }

    public static int roundsLeft(long remainingTokens, long perRoundTokens) {
        if (perRoundTokens <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, remainingTokens / perRoundTokens);
    }

    public static String digest(List<Message> convo, String memoryDigest) {
        String goal = "";
        String lastAssistant = "";
        StringBuilder tools = new StringBuilder();
        for (Message m : convo) {
            if ("user".equals(m.role()) && goal.isEmpty() && m.content() != null) goal = m.content();
            if ("assistant".equals(m.role())) {
                if (m.content() != null && !m.content().isBlank()) lastAssistant = m.content();
                for (ToolCall t : m.toolCalls()) {
                    String n = t.name();
                    if (tools.indexOf(n) < 0) tools.append(tools.isEmpty() ? "" : ", ").append(n);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("goal: ").append(oneLine(goal, 160)).append('\n');
        if (!tools.isEmpty()) sb.append("tools: ").append(tools).append('\n');
        if (!lastAssistant.isBlank()) sb.append("last: ").append(oneLine(lastAssistant, 200)).append('\n');
        if (memoryDigest != null && !memoryDigest.isBlank()) sb.append(memoryDigest);
        return sb.toString().trim();
    }

    private static String oneLine(String s, int max) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
