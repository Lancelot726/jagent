package jagent.llm;

public sealed interface Delta {

    record Text(String s) implements Delta {}

    record Reasoning(String s) implements Delta {}

    record ToolStart(int index, String name) implements Delta {}

    record Done(String reason) implements Delta {}
}
