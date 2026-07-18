package com.bishop.FinanceTracker.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Controllers in this app use blocking JPA/JDBC calls (TrackerController,
 * WealthController, etc.) but run under Spring WebFlux, whose Netty event loop has
 * only a handful of threads (sized to CPU cores). Left as-is, every blocking
 * repository call runs directly on an event-loop thread, so one slow query stalls
 * every other in-flight request (SERVER-H10).
 *
 * <p>Runs the whole request-handling chain — controller dispatch included — on
 * {@link Schedulers#boundedElastic()}, which is sized and intended for blocking
 * work, so handler methods never occupy an event-loop thread. This is the standard
 * stopgap for blocking handlers under WebFlux short of migrating to R2DBC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BlockingCallSchedulerFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).subscribeOn(Schedulers.boundedElastic());
    }
}
