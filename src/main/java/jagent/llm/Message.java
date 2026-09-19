package jagent.llm;

import jagent.json.Json;

import java.util.List;

public record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public static Message system(String c) { return new Message("system", c, List.of(), null); }

    public static Message user(String c) { return new Message("user", c, List.of(), null); }

    public static Message assistant(String c, List<ToolCall> tc) {
        return new Message("assistant", c, tc == null ? List.of() : tc, null);
    }

    public static Message tool(String callId, String content) {
        return new Message("tool", content, List.of(), callId);
    }

    public Json toJson() {
        Json.Obj o = Json.obj().put("role", role);
        o.put("content", content == null ? Json.nul() : Json.of(content));
        if (!toolCalls.isEmpty()) {
            Json.Arr a = Json.arr();
            for (ToolCall t : toolCalls) a.add(t.toJson());
            o.put("tool_calls", a);
        }
        if (toolCallId != null) o.put("tool_call_id", toolCallId);
        return o;
    }
}
