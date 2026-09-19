package jagent.term;

public final class Ansi {

    private Ansi() {}

    public static final String ESC = "\033[";

    public static final String RESET = ESC + "0m";
    public static final String BOLD = ESC + "1m";
    public static final String DIM = ESC + "2m";
    public static final String RED = ESC + "31m";
    public static final String GREEN = ESC + "32m";
    public static final String YELLOW = ESC + "33m";
    public static final String BLUE = ESC + "34m";
    public static final String MAGENTA = ESC + "35m";
    public static final String CYAN = ESC + "36m";
    public static final String GRAY = ESC + "90m";

    public static final String HIDE_CURSOR = ESC + "?25l";
    public static final String SHOW_CURSOR = ESC + "?25h";

    public static String up(int n) { return n <= 0 ? "" : ESC + n + "A"; }

    public static String down(int n) { return n <= 0 ? "" : ESC + n + "B"; }

    public static String clearLine() { return ESC + "K"; }

    public static String clearDown() { return ESC + "0J"; }

    public static String clearScreen() { return ESC + "2J" + ESC + "H"; }

    public static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '\033') {
                i++;
                while (i < s.length() && s.charAt(i) != 'm') i++;
                if (i < s.length()) i++;
                continue;
            }
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += wide(cp) ? 2 : 1;
        }
        return w;
    }

    public static boolean wide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)
                || (cp >= 0x2E80 && cp <= 0x303E)
                || (cp >= 0x3041 && cp <= 0x33FF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xA000 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE30 && cp <= 0xFE6F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x1F300 && cp <= 0x1FAFF)
                || (cp >= 0x20000 && cp <= 0x3FFFD);
    }
}
