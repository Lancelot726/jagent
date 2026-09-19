package jagent.llm;

import jagent.json.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MockClient implements ChatClient {

    private final String model;
    private final long charDelayMs;
    private final long thinkDelayMs;

    public MockClient(String model) { this(model, 8, 120); }

    public MockClient(String model, long charDelayMs, long thinkDelayMs) {
        this.model = model;
        this.charDelayMs = charDelayMs;
        this.thinkDelayMs = thinkDelayMs;
    }

    @Override
    public String model() { return model; }

    private static long toolMessages(List<Message> messages) {
        long n = 0;
        for (Message m : messages) if ("tool".equals(m.role())) n++;
        return n;
    }

    private static long folded(List<Message> messages) {
        long n = 0;
        for (Message m : messages) {
            String c = m.content();
            if (c == null || !c.startsWith("[compressed context] folded=")) continue;
            int i = c.indexOf("folded=") + 7;
            int j = i;
            while (j < c.length() && Character.isDigit(c.charAt(j))) j++;
            try {
                n += Long.parseLong(c.substring(i, j));
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    private static boolean has(List<Json> tools, String name) {
        for (Json t : tools) if (name.equals(t.at("function.name").str(""))) return true;
        return false;
    }

    private static String lastUser(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (!"user".equals(m.role())) continue;
            String c = m.content();
            if (c != null && c.startsWith("[compressed context]")) continue;
            return c;
        }
        return "";
    }

    @Override
    public Turn stream(List<Message> messages, List<Json> tools, Consumer<Delta> sink) throws IOException {
        sleep(thinkDelayMs);
        long done = toolMessages(messages) + folded(messages);
        boolean toolsOn = tools != null && !tools.isEmpty();
        String task = lastUser(messages);
        String tag = task.isBlank() ? "demo" : task.trim().split("\\s+")[0].replaceAll("[^a-zA-Z0-9_-]", "");
        if (tag.isEmpty()) tag = "demo";

        List<ToolCall> calls = new ArrayList<>();
        String say;

        boolean spawner = toolsOn && has(tools, "spawn_agent") && !tag.startsWith("sub");
        if (spawner && done == 0) {
            calls.add(new ToolCall("call_s1", "spawn_agent",
                    "{\"task\":\"sub_write_alpha\",\"label\":\"alpha\"}"));
            calls.add(new ToolCall("call_s2", "spawn_agent",
                    "{\"task\":\"sub_write_beta\",\"label\":\"beta\"}"));
            say = "并行派发两个子 agent。";
        } else if (spawner && done == 2) {
            calls.add(new ToolCall("call_w1", "write_file",
                    "{\"path\":\"demo/" + tag + "_a.txt\",\"content\":\"alpha from mock\\n\"}"));
            calls.add(new ToolCall("call_w2", "write_file",
                    "{\"path\":\"demo/" + tag + "_b.txt\",\"content\":\"beta from mock\\n\"}"));
            say = "并行写入两个文件。";
        } else if (toolsOn && done == 0) {
            calls.add(new ToolCall("call_w1", "write_file",
                    "{\"path\":\"demo/" + tag + "_a.txt\",\"content\":\"alpha from mock\\n\"}"));
            calls.add(new ToolCall("call_w2", "write_file",
                    "{\"path\":\"demo/" + tag + "_b.txt\",\"content\":\"beta from mock\\n\"}"));
            say = "并行写入两个文件。";
        } else if (toolsOn && done < (spawner ? 5 : 3)) {
            calls.add(new ToolCall("call_r1", "read_file",
                    "{\"path\":\"demo/" + tag + "_a.txt\"}"));
            say = "读回校验。";
        } else {
            calls.addAll(List.of());
            say = "完成：" + tag + "_a.txt 与 " + tag + "_b.txt 已写入并校验通过。";
        }

        for (int i = 0; i < calls.size(); i++) {
            sink.accept(new Delta.ToolStart(i, calls.get(i).name()));
            sleep(60);
        }
        for (int i = 0; i < say.length(); i++) {
            sink.accept(new Delta.Text(say.substring(i, i + 1)));
            sleep(charDelayMs);
        }
        sink.accept(new Delta.Done(calls.isEmpty() ? "stop" : "tool_calls"));

        long in = est(messages);
        long out = say.length() + calls.stream().mapToLong(c -> c.args().length()).sum();
        return new Turn(say, calls, calls.isEmpty() ? "stop" : "tool_calls", in, Math.max(1, out / 3), null);
    }

    @Override
    public Json complete(List<Message> messages, List<Json> tools) throws IOException {
        sleep(thinkDelayMs);
        return Json.obj().put("choices", Json.arr().add(
                Json.obj().put("message", Json.obj().put("role", "assistant")
                        .put("content", "mock 摘要：已完成 " + toolMessages(messages) + " 步。"))));
    }

    private static long est(List<Message> messages) {
        long n = 0;
        for (Message m : messages) {
            String c = m.content();
            if (c != null) n += c.length() / 3;
            for (ToolCall t : m.toolCalls()) n += t.args().length() / 3;
            n += 4;
        }
        return n;
    }

    private static void sleep(long ms) throws IOException {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("被中断", e);
        }
    }
}
