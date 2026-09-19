package jagent.tool;

import jagent.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class FileTools {

    private FileTools() {}

    static final int MAX_CHARS = 40_000;
    static final int MAX_HITS = 200;
    static final long MAX_FILE = 2L * 1024 * 1024;
    private static final List<String> SKIP = List.of(".git", "target", "node_modules", ".idea");

    static Path resolve(Path root, String p) throws IOException {
        if (p == null || p.isBlank()) throw new IOException("path is required");
        Path base = root.toAbsolutePath().normalize();
        Path r = base.resolve(p).normalize();
        if (!r.startsWith(base)) throw new IOException("path escapes workspace: " + p);
        return r;
    }

    static String rel(Path root, Path p) {
        return root.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    static String clip(String s) {
        return s.length() <= MAX_CHARS ? s
                : s.substring(0, MAX_CHARS) + "\n... [truncated " + (s.length() - MAX_CHARS) + " chars]";
    }

    static String text(Path p) throws IOException {
        if (!Files.exists(p)) throw new IOException("no such file: " + p.getFileName());
        if (Files.isDirectory(p)) throw new IOException("is a directory: " + p.getFileName());
        if (Files.size(p) > MAX_FILE) throw new IOException("file too large: " + Files.size(p));
        String s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        if (s.indexOf('\0') >= 0) throw new IOException("binary file");
        return s;
    }

    static List<Path> walk(Path base, int maxDepth, int maxFiles) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(base)) return out;
        try (Stream<Path> s = Files.walk(base, maxDepth)) {
            s.filter(Files::isRegularFile).filter(p -> !skipped(base, p))
                    .sorted(Comparator.comparing(Path::toString)).limit(maxFiles).forEach(out::add);
        }
        return out;
    }

    private static boolean skipped(Path base, Path p) {
        for (Path q : base.relativize(p)) if (SKIP.contains(q.toString())) return true;
        return false;
    }

    public static Tool read(Path root) {
        return new Tool("read_file", "Read a UTF-8 text file in the workspace.",
                a -> clip(text(resolve(root, a.at("path").str(null)))))
                .req("path", "string", "Path relative to the workspace root.");
    }

    public static Tool write(Path root) {
        return new Tool("write_file", "Create or overwrite a file, creating parent directories.",
                a -> {
                    String p = a.at("path").str(null);
                    Path f = resolve(root, p);
                    Path dir = f.getParent();
                    if (dir != null) Files.createDirectories(dir);
                    byte[] b = a.at("content").str("").getBytes(StandardCharsets.UTF_8);
                    Files.write(f, b);
                    return "wrote " + b.length + " bytes -> " + rel(root, f);
                })
                .req("path", "string", "Path relative to the workspace root.")
                .req("content", "string", "Full file content to write.");
    }

    public static Tool edit(Path root) {
        return new Tool("edit_file", "Replace a snippet that must occur exactly once in the file.",
                a -> {
                    Path f = resolve(root, a.at("path").str(null));
                    String old = a.at("old_string").str(null);
                    String neu = a.at("new_string").str("");
                    if (old == null || old.isEmpty()) throw new IOException("old_string is required");
                    String s = text(f);
                    int i = s.indexOf(old);
                    if (i < 0) throw new IOException("old_string not found in " + rel(root, f));
                    if (s.indexOf(old, i + old.length()) >= 0) {
                        throw new IOException("old_string is not unique in " + rel(root, f));
                    }
                    Files.write(f, (s.substring(0, i) + neu + s.substring(i + old.length()))
                            .getBytes(StandardCharsets.UTF_8));
                    return "edited " + rel(root, f) + " (-" + old.length() + " +" + neu.length() + " chars)";
                })
                .req("path", "string", "Path relative to the workspace root.")
                .req("old_string", "string", "Text to replace; must occur exactly once.")
                .req("new_string", "string", "Replacement text.");
    }

    public static Tool list(Path root) {
        return new Tool("list_files", "List files under a directory, relative to the workspace root.",
                a -> {
                    String p = a.at("path").str(".");
                    Path base = resolve(root, p);
                    int depth = (int) a.at("depth").num(4);
                    List<Path> fs = walk(base, depth, 400);
                    StringBuilder b = new StringBuilder();
                    for (Path f : fs) b.append(rel(root, f)).append('\n');
                    if (fs.isEmpty()) b.append("(no files)\n");
                    return clip(b.toString());
                })
                .opt("path", "string", "Directory to list, default the workspace root.")
                .opt("depth", "integer", "Recursion depth, default 4.");
    }

    public static Tool grep(Path root) {
        return new Tool("grep", "Search file contents by regular expression.",
                a -> {
                    String expr = a.at("pattern").str(null);
                    if (expr == null || expr.isEmpty()) throw new IOException("pattern is required");
                    Pattern re;
                    try {
                        re = Pattern.compile(expr);
                    } catch (PatternSyntaxException e) {
                        throw new IOException("bad regex: " + e.getDescription());
                    }
                    Path base = resolve(root, a.at("path").str("."));
                    StringBuilder b = new StringBuilder();
                    int hits = 0;
                    outer:
                    for (Path f : walk(base, 8, 2000)) {
                        String s;
                        try {
                            s = text(f);
                        } catch (IOException skip) {
                            continue;
                        }
                        String[] lines = s.split("\n", -1);
                        for (int i = 0; i < lines.length; i++) {
                            if (!re.matcher(lines[i]).find()) continue;
                            b.append(rel(root, f)).append(':').append(i + 1).append(':').append(lines[i]).append('\n');
                            if (++hits >= MAX_HITS) break outer;
                        }
                    }
                    if (hits == 0) b.append("(no match)\n");
                    return clip(b.toString());
                })
                .req("pattern", "string", "Java regular expression.")
                .opt("path", "string", "Directory to search, default the workspace root.");
    }
}
