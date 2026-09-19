package jagent.agent;

import jagent.core.Health;
import jagent.graph.Admission;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class Governor implements Health {

    private final Admission adm;
    private final int min;
    private final int max;
    private final double target;
    private final AtomicInteger consecFail = new AtomicInteger();
    private final AtomicInteger tripAt;
    private final AtomicInteger ups = new AtomicInteger();
    private final AtomicInteger downs = new AtomicInteger();
    private final AtomicInteger trips = new AtomicInteger();
    private volatile double errEma;
    private volatile boolean open;
    private volatile String lastAction = "";

    public Governor(Admission adm, int min, int max, double target, int tripAt) {
        this.adm = adm;
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
        this.target = target;
        this.tripAt = new AtomicInteger(tripAt);
    }

    public Governor(Admission adm) {
        this(adm, 1, 8, 0.15, 3);
    }

    @Override
    public void ok(long tokens) {
        consecFail.set(0);
        tick(true);
    }

    @Override
    public void fail() {
        int n = consecFail.incrementAndGet();
        if (n >= tripAt.get() && !open) trips.incrementAndGet();
        if (n >= tripAt.get()) open = true;
        tick(false);
    }

    @Override
    public boolean breakerOpen() { return open; }

    @Override
    public int attempt() { return consecFail.get(); }

    public void closeBreaker() {
        open = false;
        consecFail.set(0);
    }

    private synchronized void tick(boolean success) {
        errEma = 0.8 * errEma + 0.2 * (success ? 0.0 : 1.0);
        double error = errEma - target;
        if (Math.abs(error) < 0.05) return;
        int want = (int) Math.round(adm.limit() - 8.0 * error);
        want = Math.max(min, Math.min(max, want));
        int before = adm.limit();
        if (want != before) {
            adm.setLimit(want);
            int after = adm.limit();
            if (after == before) return;
            if (after < before) downs.incrementAndGet();
            else ups.incrementAndGet();
            lastAction = (after < before ? "down " : "up ") + before + "->" + after;
        }
    }

    @Override
    public void sleepBackoff(int attempt) throws InterruptedException {
        long base = Math.min(4000, 120L * (1L << Math.min(attempt, 5)));
        long jitter = ThreadLocalRandom.current().nextLong(base / 2 + 1);
        Thread.sleep(base / 2 + jitter);
    }

    public double errorRate() { return errEma; }

    public int permits() { return adm.limit(); }

    public String lastAction() { return lastAction; }

    public int ups() { return ups.get(); }

    public int downs() { return downs.get(); }

    public int trips() { return trips.get(); }

    public double valueScale() {
        return 0.85 + 1.5 * errEma;
    }
}
