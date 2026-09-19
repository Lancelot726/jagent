package jagent.core;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

public final class Bus implements AutoCloseable {

    private final SubmissionPublisher<Event> pub =
            new SubmissionPublisher<>(Runnable::run, Flow.defaultBufferSize());

    public void emit(Event e) {
        pub.submit(e);
    }

    public void onEvent(Consumer<Event> handler) {
        pub.subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(Event e) { handler.accept(e); }
            public void onError(Throwable t) { }
            public void onComplete() { }
        });
    }

    @Override
    public void close() {
        pub.close();
    }
}
