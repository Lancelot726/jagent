package jagent.graph;

import jagent.core.RunCtx;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Scheduler implements AutoCloseable {

    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    private final Graph graph;
    private final Admission adm;
    private volatile long latencyMs;

    public Scheduler(Graph graph, Admission adm) {
        this.graph = graph;
        this.adm = adm;
    }

    public Admission admission() { return adm; }

    public void latencyMs(long ms) { this.latencyMs = Math.max(0, ms); }

    public <T> Future<T> submit(String nodeId, Callable<T> work) {
        graph.state(nodeId, NodeState.READY);
        return pool.submit(() -> {
            adm.acquire();
            try {
                long d = latencyMs;
                if (d > 0) Thread.sleep(d);
                graph.state(nodeId, NodeState.RUNNING);
                try {
                    T r = RunCtx.callWith(nodeId, work);
                    graph.finish(nodeId, note(r));
                    return r;
                } catch (Exception e) {
                    graph.fail(nodeId, e.getMessage());
                    throw e;
                }
            } finally {
                adm.release();
            }
        });
    }

    private static String note(Object r) {
        if (r == null) return "";
        String s = String.valueOf(r).replace('\n', ' ');
        return s.length() <= 60 ? s : s.substring(0, 60) + "...";
    }

    @Override
    public void close() {
        pool.shutdown();
    }
}
