package jagent.tool;

import jagent.core.Env;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BashTool {

    private BashTool() {}

    private static final long MAX_OUT = 20_000;
    private static final int DEFAULT_TIMEOUT = 30;

    public static Tool create(Path root) {
        return new Tool("bash", "Run a command in the workspace root via " + Env.shellName()
                + " and return combined output. stdin is closed: a command that waits for input will hang.",
                a -> {
                    String cmd = a.at("command").str(null);
                    if (cmd == null || cmd.isBlank()) throw new IOException("command is required");
                    int sec = (int) a.at("timeout_sec").num(DEFAULT_TIMEOUT);

                    List<String> argv = new java.util.ArrayList<>(Env.shell());
                    argv.add(cmd);

                    Process p = new ProcessBuilder(argv)
                            .directory(root.toFile())
                            .redirectErrorStream(true)
                            .start();

                    try {
                        p.getOutputStream().close();
                    } catch (IOException ignored) {
                    }

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    Thread reader = new Thread(() -> {
                        try (var in = p.getInputStream()) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
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

                    String body = FileTools.clip(decode(out.toByteArray()));
                    if (body.isBlank()) body = "(no output)";
                    return done
                            ? "exit " + p.exitValue() + "\n" + body
                            : "timeout after " + sec + "s (killed)\n" + body;
                })
                .req("command", "string", "Command line for " + Env.shellName()
                        + ", run in the workspace root. Must not read stdin.")
                .opt("timeout_sec", "integer", "Wall-clock limit, default " + DEFAULT_TIMEOUT + ".");
    }

    static String decode(byte[] b) {
        Charset console = Env.consoleCharset();
        String s = new String(b, console);
        if (s.indexOf('\uFFFD') < 0) return s;
        String utf8 = new String(b, StandardCharsets.UTF_8);
        return utf8.indexOf('\uFFFD') < 0 ? utf8 : s;
    }
}
