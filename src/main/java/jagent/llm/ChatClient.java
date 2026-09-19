package jagent.llm;

import jagent.json.Json;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface ChatClient {

    Turn stream(List<Message> messages, List<Json> tools, Consumer<Delta> sink) throws IOException;

    Json complete(List<Message> messages, List<Json> tools) throws IOException;

    String model();
}
