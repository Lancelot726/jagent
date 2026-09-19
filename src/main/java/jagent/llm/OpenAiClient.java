package jagent.llm;

import jagent.json.Json;
import jagent.json.Sse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class OpenAiClient implements ChatClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutSec;

    public OpenAiClient(String baseUrl, String apiKey, String model, long timeoutSec) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSec = timeoutSec;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String model() { return model; }

    private Json.Obj body(List<Message> messages, List<Json> tools, boolean stream) {
        Json.Obj b = Json.obj().put("model", model).put("stream", stream);
        Json.Arr msgs = Json.arr();
        for (Message m : messages) msgs.add(m.toJson());
        b.put("messages", msgs);
        if (tools != null && !tools.isEmpty()) {
            Json.Arr t = Json.arr();
            for (Json x : tools) t.add(x);
            b.put("tools", t);
            b.put("tool_choice", "auto");
        }
        if (stream) b.put("stream_options", Json.obj().put("include_usage", true));
        return b;
    }

    private HttpRequest request(Json.Obj body, boolean stream) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
    }

    private HttpResponse<InputStream> send(HttpRequest req) throws IOException {
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("http " + resp.statusCode() + ": " + headline(err, 300));
            }
            return resp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    @Override
    public Turn stream(List<Message> messages, List<Json> tools, Consumer<Delta> sink) throws IOException {
        HttpResponse<InputStream> resp = send(request(body(messages, tools, true), true));

        Map<Integer, Acc> acc = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        StringBuilder reason = new StringBuilder();
        String finish = null;
        long in = 0, out = 0;

        try (Sse sse = new Sse(resp.body())) {
            String frame;
            while ((frame = sse.next()) != null) {
                if (Sse.DONE.equals(frame)) break;
                Json j;
                try {
                    j = Json.parse(frame);
                } catch (Exception bad) {
                    continue;
                }
                Json choice = j.at("choices[0]");
                if (choice != null && !choice.isNul()) {
                    Json d = choice.at("delta");
                    if (d != null && !d.isNul()) {
                        String chunk = d.at("content").str(null);
                        if (chunk != null && !chunk.isEmpty()) {
                            text.append(chunk);
                            sink.accept(new Delta.Text(chunk));
                        }
                        String rc = d.at("reasoning_content").str(null);
                        if (rc != null && !rc.isEmpty()) {
                            reason.append(rc);
                            sink.accept(new Delta.Reasoning(rc));
                        }
                        Json tcs = d.at("tool_calls");
                        if (tcs != null && !tcs.isNul()) {
                            for (int i = 0; i < tcs.size(); i++) {
                                Json tc = tcs.idx(i);
                                if (tc == null) continue;
                                int idx = (int) tc.at("index").num(i);
                                Acc a = acc.computeIfAbsent(idx, k -> new Acc());
                                String id = tc.at("id").str(null);
                                if (id != null && !id.isEmpty()) { a.id = id; a.started = true; }
                                String nm = tc.at("function.name").str(null);
                                if (nm != null && !nm.isEmpty()) { a.name = nm; }
                                String frag = tc.at("function.arguments").str(null);
                                if (frag != null && !frag.isEmpty()) a.args.append(frag);
                                if (a.started && !a.announced) {
                                    a.announced = true;
                                    sink.accept(new Delta.ToolStart(idx, a.name));
                                }
                            }
                        }
                    }
                    String fr = choice.at("finish_reason").str(null);
                    if (fr != null && !fr.isEmpty()) finish = fr;
                }
                Json usage = j.at("usage");
                if (usage != null && !usage.isNul()) {
                    in = usage.at("prompt_tokens").num(in);
                    out = usage.at("completion_tokens").num(out);
                }
            }
        }

        List<ToolCall> calls = new ArrayList<>();
        for (Map.Entry<Integer, Acc> e : acc.entrySet()) {
            Acc a = e.getValue();
            calls.add(new ToolCall(
                    a.id == null ? "call_" + e.getKey() : a.id,
                    a.name == null ? "" : a.name,
                    a.args.toString()));
        }
        sink.accept(new Delta.Done(finish == null ? "stop" : finish));
        return new Turn(text.toString(), calls, finish == null ? "stop" : finish, in, out, reason.toString());
    }

    @Override
    public Json complete(List<Message> messages, List<Json> tools) throws IOException {
        HttpResponse<InputStream> resp = send(request(body(messages, tools, false), false));
        try (InputStream in = resp.body()) {
            return Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String headline(String s, int max) {
        String one = s.replace('\n', ' ').replace('\r', ' ').trim();
        return one.length() <= max ? one : one.substring(0, max) + "...";
    }

    private static final class Acc {
        String id;
        String name;
        final StringBuilder args = new StringBuilder();
        boolean started;
        boolean announced;
    }
}
