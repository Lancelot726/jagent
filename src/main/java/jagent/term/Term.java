package jagent.term;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class Term {

    private Term() {}

    public static final PrintStream out =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    public static final PrintStream err =
            new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    public static String stdoutEncoding() {
        String v = System.getProperty("stdout.encoding");
        if (v == null) v = System.getProperty("sun.stdout.encoding");
        return v == null ? "unknown" : v;
    }

    public static String consoleEncoding() {
        return jagent.core.Env.consoleEncoding();
    }

    public static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null) return def;
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int[] size() {
        return new int[]{envInt("COLUMNS", 100), envInt("LINES", 30)};
    }

    public static boolean tty() {
        return System.console() != null;
    }
}
