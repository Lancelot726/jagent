package jagent.llm;

import jagent.core.Health;
import jagent.json.Json;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public final class FallbackClient implements ChatClient {

    private final ChatClient primary;
    private final ChatClient backup;
    private final Health health;
    private final int maxAttempts;

    public FallbackClient(ChatClient primary, ChatClient backup, Health health, int maxAttempts) {
        this.primary = primary;
        this.backup = backup;
        this.health = health;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public String model() {
        return health.breakerOpen() && backup != null
                ? backup.model() + "(backup)"
                : primary.model();
    }

    private ChatClient pick() {
        return health.breakerOpen() && backup != null ? backup : primary;
    }

    @Override
    public Turn stream(List<Message> messages, List<Json> tools, Consumer<Delta> sink) throws IOException {
        IOException last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                Turn t = pick().stream(messages, tools, sink);
                health.ok(t.inTokens() + t.outTokens());
                return t;
            } catch (IOException e) {
                last = e;
                health.fail();
                if (i + 1 < maxAttempts) {
                    try {
                        health.sleepBackoff(i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", ie);
                    }
                }
            }
        }
        if (last == null) throw new IOException("request failed");
        String msg = last.getMessage();
        throw new IOException("all " + maxAttempts + " attempts failed: "
                + (msg == null || msg.isBlank() ? last.getClass().getSimpleName() : msg), last);
    }

    @Override
    public Json complete(List<Message> messages, List<Json> tools) throws IOException {
        try {
            Json r = pick().complete(messages, tools);
            health.ok(0);
            return r;
        } catch (IOException e) {
            health.fail();
            throw e;
        }
    }
}
