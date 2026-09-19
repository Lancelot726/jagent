package jagent.graph;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

public final class Admission {

    private final Semaphore permits;
    private final AtomicInteger limit;
    private final AtomicInteger inUse = new AtomicInteger();
    private final ConcurrentHashMap<String, ReentrantLock> paths = new ConcurrentHashMap<>();
    private final AtomicInteger contended = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    public Admission(int limit) {
        this.limit = new AtomicInteger(Math.max(1, limit));
        this.permits = new Semaphore(Math.max(1, limit));
    }

    public void acquire() throws InterruptedException {
        permits.acquire();
        peak.accumulateAndGet(inUse.incrementAndGet(), Math::max);
    }

    public void release() {
        inUse.decrementAndGet();
        permits.release();
    }

    public int limit() { return limit.get(); }

    public int inUse() { return inUse.get(); }

    public synchronized void setLimit(int n) {
        int want = Math.max(1, n);
        int cur = limit.get();
        if (want > cur) {
            limit.set(want);
            permits.release(want - cur);
        } else if (want < cur) {
            int back = Math.min(cur - want, permits.availablePermits());
            if (back > 0 && permits.tryAcquire(back)) limit.set(cur - back);
        }
    }

    public void lockPath(String path) {
        ReentrantLock l = paths.computeIfAbsent(path, k -> new ReentrantLock());
        if (l.isLocked()) contended.incrementAndGet();
        l.lock();
    }

    public void unlockPath(String path) {
        ReentrantLock l = paths.get(path);
        if (l != null && l.isHeldByCurrentThread()) l.unlock();
    }

    public int pathConflicts() { return contended.get(); }

    public int peak() { return peak.get(); }
}
