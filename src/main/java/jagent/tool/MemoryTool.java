package jagent.tool;

import jagent.mem.Memory;

import java.util.List;

public final class MemoryTool {

    private MemoryTool() {}

    public static Tool remember(Memory mem) {
        return new Tool("remember", "Store one durable fact in the workspace memory so it survives compression and restarts.",
                a -> {
                    String f = a.at("fact").str(null);
                    if (f == null || f.isBlank()) throw new IllegalArgumentException("fact is required");
                    mem.remember(f);
                    return "remembered (" + mem.facts().size() + " total)";
                })
                .req("fact", "string", "A short, self-contained fact worth keeping.");
    }

    public static Tool recall(Memory mem) {
        return new Tool("recall", "Read back the compressed summary and all stored facts for this workspace.",
                a -> {
                    String d = mem.digest();
                    return d.isEmpty() ? "(memory is empty)" : d;
                });
    }

    public static Tool saveArtifact(Memory mem) {
        return new Tool("save_artifact", "Write a named artifact under work/artifacts/.",
                a -> {
                    String name = a.at("name").str(null);
                    if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
                    mem.writeArtifact(name, a.at("content").str(""));
                    return "artifact written: " + name;
                })
                .req("name", "string", "Artifact file name, e.g. notes.md")
                .req("content", "string", "Full artifact body.");
    }

    public static Tool readArtifact(Memory mem) {
        return new Tool("read_artifact", "Read a named artifact from work/artifacts/.",
                a -> mem.readArtifact(a.at("path").str(a.at("name").str(null))))
                .req("name", "string", "Artifact file name, e.g. notes.md");
    }

    public static List<Tool> all(Memory mem) {
        return List.of(remember(mem), recall(mem), saveArtifact(mem), readArtifact(mem));
    }
}
