package jagent.agent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public final class Budget {

    private final long cap;
    private final AtomicLong spent = new AtomicLong();
    private final DoubleAdder credits = new DoubleAdder();
    private final long startNs = System.nanoTime();

    public Budget(long capTokens) {
        this.cap = Math.max(1000, capTokens);
    }

    public long cap() { return cap; }

    public long spent() { return spent.get(); }

    public long remaining() { return Math.max(0, cap - spent.get()); }

    public double usedFraction() { return (double) spent.get() / cap; }

    public boolean canAfford(long est) { return remaining() >= Math.max(1, est) / 4; }

    public long quota(int expectedStepsLeft) {
        return remaining() / Math.max(1, expectedStepsLeft);
    }

    public void charge(long in, long out) {
        spent.addAndGet(Math.max(0, in) + Math.max(0, out));
    }

    public void credit(double profit) {
        credits.add(profit);
    }

    public double credits() { return credits.sum(); }

    public double burnPerSec() {
        double s = (System.nanoTime() - startNs) / 1e9;
        return s <= 0.5 ? 0 : spent.get() / s;
    }
}
