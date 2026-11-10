package com.asp.platform.billing.infrastructure;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/// Scripts the faults of the sandbox providers, for tests and local trials. Each queue is consumed one entry per call;
/// an empty queue means the call succeeds. Nothing here is reachable over HTTP.
@Component
public class SandboxScenario {

    /// What the next call does instead of succeeding.
    public enum Fault {
        /// The provider refuses the request (definitive).
        REJECT,
        /// The provider cannot be reached; nothing was done.
        UNAVAILABLE,
        /// The document is issued, then the answer is lost (timeout): the outcome is unknown to the caller.
        UNAVAILABLE_AFTER_ISSUE,
        /// The document is issued with a total that is one cent more than asked.
        WRONG_TOTAL
    }

    /// Makes a call wait inside the provider: `entered` is counted down when the call arrives, then the call waits
    /// until `release` is counted down (at most 10 s).
    public record Hold(CountDownLatch entered, CountDownLatch release) {

        public Hold() {
            this(new CountDownLatch(1), new CountDownLatch(1));
        }
    }

    private final Queue<Fault> invoicing = new ConcurrentLinkedQueue<>();
    private final Queue<Fault> voids = new ConcurrentLinkedQueue<>();
    private final Queue<Fault> payments = new ConcurrentLinkedQueue<>();
    private final AtomicReference<@Nullable Hold> invoicingHold = new AtomicReference<>();
    private final AtomicReference<@Nullable Hold> voidHold = new AtomicReference<>();

    /// The next issue (invoice or credit note) calls.
    public void failInvoicing(Fault... faults) {
        invoicing.addAll(List.of(faults));
    }

    public void failVoids(Fault... faults) {
        voids.addAll(List.of(faults));
    }

    /// The next payment creations (Multibanco reference, MB WAY request).
    public void failPayments(Fault... faults) {
        payments.addAll(List.of(faults));
    }

    /// The next issue call waits in the provider, before it does anything.
    public void holdNextInvoicing(Hold hold) {
        invoicingHold.set(hold);
    }

    /// The next void call waits in the provider.
    public void holdNextVoid(Hold hold) {
        voidHold.set(hold);
    }

    public void reset() {
        invoicing.clear();
        voids.clear();
        payments.clear();
        invoicingHold.set(null);
        voidHold.set(null);
    }

    @Nullable
    Fault nextInvoicing() {
        return invoicing.poll();
    }

    @Nullable
    Fault nextVoid() {
        return voids.poll();
    }

    @Nullable
    Fault nextPayment() {
        return payments.poll();
    }

    void awaitInvoicingHold() {
        await(invoicingHold.getAndSet(null));
    }

    void awaitVoidHold() {
        await(voidHold.getAndSet(null));
    }

    private static void await(@Nullable Hold hold) {
        if (hold == null) {
            return;
        }
        hold.entered().countDown();
        try {
            hold.release().await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
