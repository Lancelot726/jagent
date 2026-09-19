package jagent.tool;

import jagent.json.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();
    private final Path root;

    public ToolRegistry(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public ToolRegistry add(Tool t) {
        byName.put(t.name(), t);
        return this;
    }

    public static ToolRegistry standard(Path root) {
        return new ToolRegistry(root)
                .add(FileTools.read(root))
                .add(FileTools.write(root))
                .add(FileTools.edit(root))
                .add(FileTools.list(root))
                .add(FileTools.grep(root))
                .add(BashTool.create(root));
    }

    public Path root() { return root; }

    public List<Json> schemas() {
        List<Json> l = new ArrayList<>();
        for (Tool t : byName.values()) l.add(t.schema());
        return l;
    }

    public List<String> names() { return new ArrayList<>(byName.keySet()); }

    public boolean has(String name) { return byName.containsKey(name); }

    public String call(String name, String argsJson) {
        Tool t = byName.get(name);
        if (t == null) return "error: unknown tool " + name;
        return t.call(argsJson);
    }
}
