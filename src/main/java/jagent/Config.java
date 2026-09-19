package jagent;

import jagent.term.Lang;
import jagent.term.Term;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public final class Config {

    public static final String APP = "jagent";
    public static final String VERSION = "0.1.0";

    public final String provider;
    public final String baseUrl;
    public final String apiKey;
    public final String model;
    public final Lang lang;
    public final Path cwd;
    public final int width;
    public final int height;
    public final int permits;
    public final long toolDelayMs;
    public final boolean graph;
    public final String modelStrong;
    public final long budget;
    public final long compressThreshold;
    public final boolean resume;

    private Config(String provider, String baseUrl, String apiKey, String model, Lang lang,
                   Path cwd, int width, int height, int permits, long toolDelayMs, boolean graph,
                   String modelStrong, long budget, long compressThreshold, boolean resume) {
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.lang = lang;
        this.cwd = cwd;
        this.width = width;
        this.height = height;
        this.permits = permits;
        this.toolDelayMs = toolDelayMs;
        this.graph = graph;
        this.modelStrong = modelStrong;
        this.budget = budget;
        this.compressThreshold = compressThreshold;
        this.resume = resume;
    }

    public boolean isMock() { return "mock".equals(provider); }

    public static Config load(Map<String, String> cli) {
        Path cwd = Path.of(cli.getOrDefault("cwd", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        Properties p = loadProps(cwd);
        int[] term = Term.size();
        return new Config(
                pick(cli, p, "provider", "JAGENT_PROVIDER", "mock"),
                pick(cli, p, "base-url", "JAGENT_BASE_URL", "https://api.openai.com/v1"),
                pick(cli, p, "api-key", "JAGENT_API_KEY", ""),
                pick(cli, p, "model", "JAGENT_MODEL", "gpt-4o-mini"),
                resolveLang(cli, p, cwd),
                cwd,
                intOr(cli.get("width"), term[0]),
                intOr(cli.get("height"), term[1]),
                intPick(cli, p, "permits", "JAGENT_PERMITS", 4),
                intPick(cli, p, "tool-delay", "JAGENT_TOOL_DELAY", 0),
                !flag(cli, p, "no-graph", "JAGENT_NO_GRAPH"),
                pick(cli, p, "model-strong", "JAGENT_MODEL_STRONG", ""),
                longPick(cli, p, "budget", "JAGENT_BUDGET", 40_000),
                longPick(cli, p, "compress-after", "JAGENT_COMPRESS_AFTER", 6_000),
                flag(cli, p, "resume", "JAGENT_RESUME"));
    }

    private static long longPick(Map<String, String> cli, Properties p, String key, String env, long def) {
        String v = cli.get(key);
        if (v == null) v = System.getenv(env);
        if (v == null) v = p.getProperty(key);
        if (v == null) return def;
        try {
            long n = Long.parseLong(v.trim());
            return n > 0 ? n : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean flag(Map<String, String> cli, Properties p, String key, String env) {
        if (cli.containsKey(key) && !"false".equalsIgnoreCase(cli.get(key))) return true;
        String e = System.getenv(env);
        if (e != null && !e.isBlank() && !"0".equals(e) && !"false".equalsIgnoreCase(e)) return true;
        String v = p.getProperty(key);
        return v != null && !v.isBlank() && !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    private static int intPick(Map<String, String> cli, Properties p, String key, String env, int def) {
        String v = cli.get(key);
        if (v == null) v = System.getenv(env);
        if (v == null) v = p.getProperty(key);
        return intOr(v, def);
    }

    public String systemPrompt() {
        return resource("/prompt/system.txt").replace("{{user_lang}}", lang.tag());
    }

    public static String resource(String path) {
        try (InputStream in = Config.class.getResourceAsStream(path)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static Lang resolveLang(Map<String, String> cli, Properties p, Path cwd) {
        String v = cli.get("user-lang");
        if (v == null) v = System.getenv("JAGENT_USER_LANG");
        if (v == null) v = p.getProperty("user-lang");
        if (v != null && !v.isBlank()) return Lang.of(v);
        if (System.console() == null) return Lang.ZH;
        Lang picked = promptLang();
        save(cwd, "user-lang", picked.code());
        return picked;
    }

    private static Lang promptLang() {
        Lang chrome = Lang.ENG;
        Term.out.println();
        Term.out.println(chrome.t("firstrun.title"));
        Term.out.println("  " + chrome.t("firstrun.option1"));
        Term.out.println("  " + chrome.t("firstrun.option2"));
        Term.out.print(chrome.t("firstrun.prompt") + " ");
        Term.out.flush();
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        String line = sc.hasNextLine() ? sc.nextLine().trim() : "";
        return "2".equals(line) ? Lang.ENG : Lang.ZH;
    }

    public static void save(Path cwd, String key, String value) {
        Path f = cwd.resolve("work").resolve("jagent.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
            }
        }
        p.setProperty(key, value);
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                p.store(w, "jagent");
            }
        } catch (IOException ignored) {
        }
    }

    private static Properties loadProps(Path cwd) {
        Properties p = new Properties();
        for (Path f : new Path[]{
                cwd.resolve("work").resolve("jagent.properties"),
                cwd.resolve("jagent.properties")}) {
            if (!Files.isRegularFile(f)) continue;
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
            }
        }
        return p;
    }

    private static String pick(Map<String, String> cli, Properties p, String key, String env, String def) {
        String v = cli.get(key);
        if (v == null) v = System.getenv(env);
        if (v == null) v = p.getProperty(key);
        if (v == null || v.isBlank()) v = def;
        return v == null ? "" : v.trim();
    }

    private static int intOr(String s, int def) {
        if (s == null) return def;
        try {
            int n = Integer.parseInt(s.trim());
            return n > 0 ? n : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
