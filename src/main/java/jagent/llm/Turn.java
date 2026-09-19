package jagent.llm;

import java.util.List;

public record Turn(String content, List<ToolCall> toolCalls, String finishReason, long inTokens, long outTokens,
                   String reasoning) {

    public boolean hasTools() { return !toolCalls.isEmpty(); }

    public static Turn text(String content) {
        return new Turn(content, List.of(), "stop", 0, 0, null);
    }
}
