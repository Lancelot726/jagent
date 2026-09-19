package jagent.term;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public enum Lang {

    ZH("zh"),
    ENG("eng");

    private final String code;
    private final Properties props;

    Lang(String code) {
        this.code = code;
        this.props = new Properties();
        try (InputStream in = Lang.class.getResourceAsStream("/lang/" + code + ".properties")) {
            if (in != null) props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public String code() { return code; }

    public String tag() { return this == ZH ? "Zh" : "Eng"; }

    public static Lang of(String s) {
        if (s == null) return ZH;
        return switch (s.trim().toLowerCase()) {
            case "eng", "en", "english" -> ENG;
            default -> ZH;
        };
    }

    public String t(String key, Object... args) {
        String v = props.getProperty(key);
        if (v == null) return key;
        return args.length == 0 ? v : String.format(v, args);
    }
}
