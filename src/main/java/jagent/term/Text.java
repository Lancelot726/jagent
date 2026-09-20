package jagent.term;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private Text() {}

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)");

    public static String plain(String s) {
        if (s == null || s.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        boolean fence = false;
        for (String raw : s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String t = raw.strip();
            if (t.startsWith("```") || t.startsWith("~~~")) {
                fence = !fence;
                continue;
            }
            out.add(fence ? "  " + raw : line(inline(raw)));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
        return String.join("\n", out);
    }

    private static String line(String raw) {
        String indent = raw.substring(0, raw.length() - raw.stripLeading().length());
        String t = raw.strip();
        if (t.isEmpty()) return "";
        int h = 0;
        while (h < t.length() && t.charAt(h) == '#') h++;
        if (h > 0 && h <= 6 && h < t.length() && t.charAt(h) == ' ') return indent + t.substring(h + 1).strip();
        if (t.startsWith("> ")) return indent + t.substring(2).strip();
        if (t.length() > 1 && (t.charAt(0) == '-' || t.charAt(0) == '*' || t.charAt(0) == '+')
                && t.charAt(1) == ' ') return indent + "· " + t.substring(2).strip();
        return indent + t;
    }

    private static String inline(String s) {
        String r = s.replace("**", "").replace("__", "").replaceAll("`+", "");
        Matcher m = LINK.matcher(r);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String text = m.group(1).isBlank() ? m.group(2) : m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(text));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
