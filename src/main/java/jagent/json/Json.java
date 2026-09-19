package jagent.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public sealed interface Json {

    record Obj(LinkedHashMap<String, Json> v) implements Json {
        public Obj put(String k, Json val) { v.put(k, val); return this; }
        public Obj put(String k, String val) { v.put(k, Json.of(val)); return this; }
        public Obj put(String k, long val) { v.put(k, Json.of(val)); return this; }
        public Obj put(String k, boolean val) { v.put(k, Json.of(val)); return this; }
        public Json get(String k) { return v.get(k); }
    }

    record Arr(List<Json> v) implements Json {
        public Arr add(Json j) { v.add(j); return this; }
    }

    record Str(String v) implements Json {}

    record Num(String v) implements Json {}

    record Bool(boolean v) implements Json {}

    record Nul() implements Json {}

    static Obj obj() { return new Obj(new LinkedHashMap<>()); }

    static Arr arr() { return new Arr(new ArrayList<>()); }

    static Json nul() { return new Nul(); }

    static Json of(String s) { return s == null ? new Nul() : new Str(s); }

    static Json of(long n) { return new Num(Long.toString(n)); }

    static Json of(boolean b) { return new Bool(b); }

    static Json parse(String text) { return new JsonParser(text).document(); }

    static String write(Json j) {
        StringBuilder b = new StringBuilder();
        write(j, b);
        return b.toString();
    }

    static void write(Json j, StringBuilder b) {
        switch (j) {
            case Obj o -> {
                b.append('{');
                boolean first = true;
                for (Map.Entry<String, Json> e : o.v().entrySet()) {
                    if (!first) b.append(',');
                    first = false;
                    quote(e.getKey(), b);
                    b.append(':');
                    write(e.getValue(), b);
                }
                b.append('}');
            }
            case Arr a -> {
                b.append('[');
                List<Json> l = a.v();
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) b.append(',');
                    write(l.get(i), b);
                }
                b.append(']');
            }
            case Str s -> quote(s.v(), b);
            case Num n -> b.append(n.v());
            case Bool bo -> b.append(bo.v());
            case Nul ignored -> b.append("null");
        }
    }

    static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }

    default Json at(String path) {
        Json cur = this;
        for (String seg : path.split("\\.")) {
            String name = seg;
            int br;
            while ((br = name.indexOf('[')) >= 0) {
                int end = name.indexOf(']', br);
                if (end < 0) return nul();
                String head = name.substring(0, br);
                if (!head.isEmpty()) {
                    if (!(cur instanceof Obj o)) return nul();
                    cur = o.v().get(head);
                    if (cur == null) return nul();
                }
                int idx;
                try {
                    idx = Integer.parseInt(name.substring(br + 1, end).trim());
                } catch (NumberFormatException e) {
                    return nul();
                }
                if (!(cur instanceof Arr a) || idx < 0 || idx >= a.v().size()) return nul();
                cur = a.v().get(idx);
                name = name.substring(end + 1);
            }
            if (!name.isEmpty()) {
                if (!(cur instanceof Obj o)) return nul();
                cur = o.v().get(name);
                if (cur == null) return nul();
            }
        }
        return cur;
    }

    default Json idx(int i) {
        return this instanceof Arr a && i >= 0 && i < a.v().size() ? a.v().get(i) : nul();
    }

    default String str(String def) { return this instanceof Str s ? s.v() : def; }

    default String str() { return str(""); }

    default long num(long def) {
        if (this instanceof Num n) {
            try {
                return Long.parseLong(n.v());
            } catch (NumberFormatException e) {
                try {
                    return (long) Double.parseDouble(n.v());
                } catch (NumberFormatException e2) {
                    return def;
                }
            }
        }
        return def;
    }

    default boolean bool(boolean def) { return this instanceof Bool b ? b.v() : def; }

    default Obj asObj() { return this instanceof Obj o ? o : obj(); }

    default Arr asArr() { return this instanceof Arr a ? a : arr(); }

    default int size() {
        return this instanceof Arr a ? a.v().size()
                : this instanceof Obj o ? o.v().size()
                : this instanceof Str s ? s.v().length()
                : 0;
    }

    default boolean isNul() { return this instanceof Nul; }
}

final class JsonParser {

    private final String s;
    private int i;

    JsonParser(String s) { this.s = s; }

    Json document() {
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') i = 1;
        skipWs();
        Json v = value();
        skipWs();
        if (i < s.length()) throw err("trailing content");
        return v;
    }

    private Json value() {
        if (i >= s.length()) throw err("unexpected end of input");
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> new Json.Str(string());
            case 't' -> { literal("true"); yield new Json.Bool(true); }
            case 'f' -> { literal("false"); yield new Json.Bool(false); }
            case 'n' -> { literal("null"); yield new Json.Nul(); }
            default -> number();
        };
    }

    private Json.Obj object() {
        i++;
        Json.Obj o = Json.obj();
        skipWs();
        if (peek() == '}') { i++; return o; }
        while (true) {
            skipWs();
            if (peek() != '"') throw err("expected object key");
            String k = string();
            skipWs();
            if (peek() != ':') throw err("expected ':'");
            i++;
            skipWs();
            o.v().put(k, value());
            skipWs();
            char c = next();
            if (c == '}') return o;
            if (c != ',') throw err("expected ',' or '}'");
        }
    }

    private Json.Arr array() {
        i++;
        Json.Arr a = Json.arr();
        skipWs();
        if (peek() == ']') { i++; return a; }
        while (true) {
            skipWs();
            a.v().add(value());
            skipWs();
            char c = next();
            if (c == ']') return a;
            if (c != ',') throw err("expected ',' or ']'");
        }
    }

    private String string() {
        if (next() != '"') throw err("expected '\"'");
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw err("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw err("unterminated escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"' -> b.append('"');
                case '\\' -> b.append('\\');
                case '/' -> b.append('/');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw err("bad \\u escape");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw err("bad escape \\" + e);
            }
        }
    }

    private Json number() {
        int start = i;
        if (peek() == '-' || peek() == '+') i++;
        boolean digit = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') { digit = true; i++; }
            else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') i++;
            else break;
        }
        if (!digit) throw err("expected value");
        return new Json.Num(s.substring(start, i));
    }

    private void literal(String lit) {
        if (!s.startsWith(lit, i)) throw err("expected " + lit);
        i += lit.length();
    }

    private void skipWs() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }

    private char peek() { return i < s.length() ? s.charAt(i) : '\0'; }

    private char next() {
        if (i >= s.length()) throw err("unexpected end of input");
        return s.charAt(i++);
    }

    private RuntimeException err(String msg) {
        int a = Math.max(0, i - 40);
        int b = Math.min(s.length(), i + 40);
        String ctx = s.substring(a, b).replace("\n", "\\n").replace("\r", "");
        return new IllegalArgumentException("json " + msg + " at " + i + " | " + ctx);
    }
}
