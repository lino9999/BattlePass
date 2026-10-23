package com.Lino.battlePass.managers;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Orders mutations without blocking executor threads; failed critical writes fence later operations. */
final class DatabaseWriteQueue {
    private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private boolean closing;
    private final Consumer<Throwable> onCriticalFailure;

    DatabaseWriteQueue() {
        this(failure -> {});
    }

    DatabaseWriteQueue(Consumer<Throwable> onCriticalFailure) {
        this.onCriticalFailure = onCriticalFailure;
    }

    synchronized <T> CompletableFuture<T> write(Supplier<CompletableFuture<T>> operation) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Database is shutting down"));
        CompletableFuture<T> result = writes.thenCompose(ignored -> operation.get());
        writes = result.thenApply(ignored -> null);
        result.whenComplete((value, failure) -> {
            if (failure != null) onCriticalFailure.accept(failure);
        });
        return track(result);
    }

    synchronized <T> CompletableFuture<T> read(Supplier<CompletableFuture<T>> operation) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Database is shutting down"));
        return track(writes.thenCompose(ignored -> operation.get()));
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        pending.add(future);
        future.whenComplete((result, failure) -> pending.remove(future));
        return future;
    }

    void closeAndAwait() throws Exception {
        synchronized (this) { closing = true; }
        CompletableFuture<?>[] outstanding = pending.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(outstanding).get(5, TimeUnit.SECONDS);
    }
}
