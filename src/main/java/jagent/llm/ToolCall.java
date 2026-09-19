package jagent.llm;

import jagent.json.Json;

public record ToolCall(String id, String name, String args) {

    public Json toJson() {
        return Json.obj()
                .put("id", id)
                .put("type", "function")
                .put("function", Json.obj().put("name", name).put("arguments", args));
    }

    public Json argsJson() {
        try {
            return Json.parse(args == null || args.isBlank() ? "{}" : args);
        } catch (Exception e) {
            return Json.obj();
        }
    }
}
