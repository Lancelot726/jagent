package jagent.core;

import java.nio.charset.Charset;
import java.util.List;

public final class Env {

    private Env() {}

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static List<String> shell() {
        return windows() ? List.of("cmd.exe", "/c") : List.of("/bin/sh", "-c");
    }

    public static String shellName() {
        return String.join(" ", shell());
    }

    public static String os() {
        return System.getProperty("os.name", "unknown") + " "
                + System.getProperty("os.version", "") + " "
                + System.getProperty("os.arch", "");
    }

    public static Charset consoleCharset() {
        String v = System.getProperty("native.encoding");
        if (v != null && !v.isBlank()) {
            try {
                return Charset.forName(v);
            } catch (Exception ignored) {
            }
        }
        return Charset.defaultCharset();
    }

    public static String consoleEncoding() {
        return consoleCharset().name();
    }
}
