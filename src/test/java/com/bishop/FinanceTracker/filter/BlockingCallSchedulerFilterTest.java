package com.bishop.FinanceTracker.filter;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingCallSchedulerFilterTest {

    private final BlockingCallSchedulerFilter filter = new BlockingCallSchedulerFilter();

    @Test
    void movesDownstreamChainOffTheCallingThread() {
        AtomicReference<String> threadName = new AtomicReference<>();
        String callingThread = Thread.currentThread().getName();
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);

        Mono<Void> result = filter.filter(exchange, ex -> Mono.fromRunnable(() ->
                threadName.set(Thread.currentThread().getName())));
        result.block();

        assertTrue(threadName.get().startsWith("boundedElastic"),
                "expected downstream chain to run on boundedElastic, ran on " + threadName.get());
        assertNotEquals(callingThread, threadName.get());
    }
}
