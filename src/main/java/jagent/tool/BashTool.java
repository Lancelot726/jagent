package jagent.tool;

import jagent.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BashTool {

    private BashTool() {}

    private static final long MAX_OUT = 20_000;
    private static final int DEFAULT_TIMEOUT = 30;

    private static List<String> shell() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? List.of("cmd.exe", "/c")
                : List.of("/bin/sh", "-c");
    }

    public static Tool create(Path root) {
        return new Tool("bash", "Run a shell command in the workspace root and return combined output.",
                a -> {
                    String cmd = a.at("command").str(null);
                    if (cmd == null || cmd.isBlank()) throw new IOException("command is required");
                    int sec = (int) a.at("timeout_sec").num(DEFAULT_TIMEOUT);

                    List<String> argv = new java.util.ArrayList<>(shell());
                    argv.add(cmd);

                    Process p = new ProcessBuilder(argv)
                            .directory(root.toFile())
                            .redirectErrorStream(true)
                            .start();

                    StringBuilder out = new StringBuilder();
                    Thread reader = new Thread(() -> {
                        try (var in = p.getInputStream()) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                        }
                    });
                    reader.setDaemon(true);
                    reader.start();

                    boolean done;
                    try {
                        done = p.waitFor(sec, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        p.destroyForcibly();
                        throw new IOException("interrupted");
                    }
                    if (!done) {
                        p.destroyForcibly();
                        p.waitFor(3, TimeUnit.SECONDS);
                    }
                    reader.join(2000);
                    p.getInputStream().close();

                    String body = FileTools.clip(out.toString());
                    if (body.isBlank()) body = "(no output)";
                    return done
                            ? "exit " + p.exitValue() + "\n" + body
                            : "timeout after " + sec + "s (killed)\n" + body;
                })
                .req("command", "string", "Shell command, run in the workspace root.")
                .opt("timeout_sec", "integer", "Wall-clock limit, default " + DEFAULT_TIMEOUT + ".");
    }
}
