package jagent.mem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkDir {

    public static final String PLAN = "plan.md";
    public static final String FACTS = "facts.md";
    public static final String SUMMARY = "summary.md";
    public static final String EPISODES = "episodes.log";
    public static final String ARTIFACTS = "artifacts";
    public static final String SCORECARD = "scorecard.txt";

    private final Path root;

    public WorkDir(Path workspace) {
        this.root = workspace.resolve("work");
    }

    public Path root() { return root; }

    public Path path(String name) { return root.resolve(name); }

    public static boolean isResumable(Path workspace) {
        return Files.isRegularFile(new WorkDir(workspace).path(PLAN));
    }

    public void init() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(root.resolve(ARTIFACTS));
        seed(FACTS, "# facts\n\n");
        seed(SUMMARY, "# summary\n\n");
        seed(EPISODES, "");
    }

    private void seed(String name, String header) throws IOException {
        Path f = path(name);
        if (!Files.exists(f)) Files.writeString(f, header, StandardCharsets.UTF_8);
    }

    public String read(String name) throws IOException {
        Path f = path(name);
        return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
    }

    public void write(String name, String body) throws IOException {
        Files.createDirectories(root);
        Files.writeString(path(name), body == null ? "" : body, StandardCharsets.UTF_8);
    }

    public void append(String name, String line) throws IOException {
        Files.createDirectories(root);
        Path f = path(name);
        if (!Files.exists(f)) Files.writeString(f, "", StandardCharsets.UTF_8);
        Files.writeString(f, line + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
    }
}
