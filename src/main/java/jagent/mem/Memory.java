package jagent.mem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Memory {

    private static final int MAX_FACTS = 200;
    private static final int MAX_RUNS = 20;
    private static final int MAX_ARTIFACT_CHARS = 40_000;

    private final WorkDir dir;
    private final List<String> facts = new ArrayList<>();
    private final List<String> runs = new ArrayList<>();
    private String summary = "";

    public Memory(WorkDir dir) throws IOException {
        this.dir = dir;
        dir.init();
        for (String line : dir.read(WorkDir.FACTS).split("\n")) {
            String t = line.trim();
            if (t.startsWith("- ")) facts.add(t.substring(2).trim());
        }
        for (String line : dir.read(WorkDir.RUNS).split("\n")) {
            String t = line.trim();
            if (t.startsWith("- ")) runs.add(t.substring(2).trim());
        }
        summary = dir.read(WorkDir.SUMMARY).replaceFirst("(?s)^# summary\\s*", "").trim();
    }

    public synchronized List<String> facts() { return List.copyOf(facts); }

    public synchronized String summary() { return summary; }

    public synchronized void remember(String fact) throws IOException {
        String f = fact == null ? "" : fact.replace('\n', ' ').trim();
        if (f.isEmpty()) return;
        facts.add(f);
        while (facts.size() > MAX_FACTS) facts.remove(0);
        rewriteFacts();
    }

    public synchronized void forget(String fact) throws IOException {
        facts.removeIf(f -> f.equals(fact));
        rewriteFacts();
    }

    public synchronized void summarize(String text) throws IOException {
        summary = text == null ? "" : text.trim();
        dir.write(WorkDir.SUMMARY, "# summary\n\n" + summary + "\n");
    }

    public synchronized List<String> runs() { return List.copyOf(runs); }

    public synchronized void recordRun(String line) throws IOException {
        String r = line == null ? "" : oneLine(line);
        if (r.isEmpty()) return;
        runs.add(r);
        while (runs.size() > MAX_RUNS) runs.remove(0);
        rewriteRuns();
    }

    public void episode(int step, String actor, String event) throws IOException {
        dir.append(WorkDir.EPISODES, "[" + step + "] " + actor + " " + oneLine(event));
    }

    public Path artifact(String name) throws IOException {
        Path p = dir.root().resolve(WorkDir.ARTIFACTS).resolve(safe(name)).normalize();
        Path base = dir.root().resolve(WorkDir.ARTIFACTS).toAbsolutePath().normalize();
        if (!p.toAbsolutePath().normalize().startsWith(base)) throw new IOException("bad artifact name");
        Files.createDirectories(base);
        return p;
    }

    public String readArtifact(String name) throws IOException {
        Path p = artifact(name);
        if (!Files.isRegularFile(p)) throw new IOException("no such artifact: " + name);
        String s = Files.readString(p, StandardCharsets.UTF_8);
        return s.length() <= MAX_ARTIFACT_CHARS ? s : s.substring(0, MAX_ARTIFACT_CHARS);
    }

    public void writeArtifact(String name, String body) throws IOException {
        Files.writeString(artifact(name), body == null ? "" : body, StandardCharsets.UTF_8);
    }

    public String digest() {
        StringBuilder sb = new StringBuilder();
        if (!summary.isEmpty()) sb.append("summary: ").append(summary).append('\n');
        if (!runs.isEmpty()) {
            sb.append("earlier runs, newest last:\n");
            for (String r : runs) sb.append("- ").append(r).append('\n');
        }
        if (!facts.isEmpty()) {
            sb.append("facts:\n");
            for (String f : facts) sb.append("- ").append(f).append('\n');
        }
        return sb.toString().trim();
    }

    private void rewriteFacts() throws IOException {
        StringBuilder sb = new StringBuilder("# facts\n\n");
        for (String f : facts) sb.append("- ").append(f).append('\n');
        dir.write(WorkDir.FACTS, sb.toString());
    }

    private void rewriteRuns() throws IOException {
        StringBuilder sb = new StringBuilder("# runs\n\n");
        for (String r : runs) sb.append("- ").append(r).append('\n');
        dir.write(WorkDir.RUNS, sb.toString());
    }

    private static String safe(String name) throws IOException {
        if (name == null || name.isBlank()) throw new IOException("artifact name required");
        return name.replace('\\', '/').replace("../", "");
    }

    private static String oneLine(String s) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() <= 120 ? t : t.substring(0, 120) + "…";
    }
}
