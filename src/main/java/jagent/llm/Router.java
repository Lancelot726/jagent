package jagent.llm;

import java.util.EnumMap;
import java.util.Map;

public final class Router {

    public enum Tier { CHEAP, STRONG }

    private final Map<Tier, ChatClient> clients = new EnumMap<>(Tier.class);

    public Router(ChatClient cheap, ChatClient strong) {
        clients.put(Tier.CHEAP, cheap);
        clients.put(Tier.STRONG, strong);
    }

    public ChatClient of(Tier t) {
        ChatClient c = clients.get(t);
        return c == null ? clients.get(Tier.CHEAP) : c;
    }

    public String model(Tier t) {
        return of(t).model();
    }
}
