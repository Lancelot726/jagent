package jagent.core;

public interface Health {

    void ok(long tokens);

    void fail();

    boolean breakerOpen();

    int attempt();

    void sleepBackoff(int attempt) throws InterruptedException;
}
