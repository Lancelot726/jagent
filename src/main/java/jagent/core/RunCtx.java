package jagent.core;

import java.util.concurrent.Callable;

public final class RunCtx {

    private RunCtx() {}

    public static final ScopedValue<String> CUR = ScopedValue.newInstance();

    public static <T> T callWith(String node, Callable<T> work) throws Exception {
        return ScopedValue.where(CUR, node).call(() -> work.call());
    }

    public static void runWith(String node, Runnable work) {
        ScopedValue.where(CUR, node).run(work);
    }

    public static String current() {
        return CUR.isBound() ? CUR.get() : null;
    }
}
