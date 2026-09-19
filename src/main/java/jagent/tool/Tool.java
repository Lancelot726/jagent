package jagent.tool;

import jagent.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Tool {

    @FunctionalInterface
    public interface Body {
        String run(Json args) throws Exception;
    }

    private final String name;
    private final String desc;
    private final Map<String, Json> props = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();
    private final Body body;

    public Tool(String name, String desc, Body body) {
        this.name = name;
        this.desc = desc;
        this.body = body;
    }

    public Tool req(String n, String type, String d) {
        props.put(n, prop(type, d));
        required.add(n);
        return this;
    }

    public Tool opt(String n, String type, String d) {
        props.put(n, prop(type, d));
        return this;
    }

    public String name() { return name; }

    public Json.Obj schema() {
        Json.Obj ps = Json.obj();
        for (Map.Entry<String, Json> e : props.entrySet()) ps.put(e.getKey(), e.getValue());
        Json.Obj params = Json.obj().put("type", "object").put("properties", ps);
        if (!required.isEmpty()) {
            Json.Arr req = Json.arr();
            for (String r : required) req.add(Json.of(r));
            params.put("required", req);
        }
        return Json.obj()
                .put("type", "function")
                .put("function", Json.obj()
                        .put("name", name)
                        .put("description", desc)
                        .put("parameters", params));
    }

    public String call(String argsJson) {
        try {
            Json a = argsJson == null || argsJson.isBlank() ? Json.obj() : Json.parse(argsJson);
            String r = body.run(a);
            return r == null ? "ok" : r;
        } catch (Exception e) {
            String m = e.getMessage();
            return "error: " + (m == null ? e.getClass().getSimpleName() : m);
        }
    }

    private static Json prop(String type, String d) {
        return Json.obj().put("type", type).put("description", d);
    }
}
