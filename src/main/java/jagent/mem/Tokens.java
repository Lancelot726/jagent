package jagent.mem;

import jagent.llm.Message;
import jagent.llm.ToolCall;

import java.util.List;

public final class Tokens {

    private Tokens() {}

    public static long estimate(String s) {
        if (s == null || s.isEmpty()) return 0;
        long ascii = 0, wide = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 128) ascii++;
            else wide++;
        }
        return ascii / 4 + wide * 2L / 3;
    }

    public static long estimate(List<Message> messages) {
        long n = 0;
        for (Message m : messages) {
            n += 4 + estimate(m.content());
            for (ToolCall t : m.toolCalls()) n += estimate(t.args()) + 8;
        }
        return n;
    }
}
