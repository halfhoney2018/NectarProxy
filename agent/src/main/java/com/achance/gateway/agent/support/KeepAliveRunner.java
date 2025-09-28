package com.achance.gateway.agent.support;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SmartLifecycle-driven keeper to keep non-web app alive.
 * Spawns a non-daemon thread that blocks on Mono.never(), decoupling process lifetime
 * from any specific RSocketRequester instance (we already have reconnect logic elsewhere).
 */
@Component
public class KeepAliveRunner implements SmartLifecycle {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread keeper;

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            keeper = new Thread(() -> Mono.never().block(), "agent-keepalive");
            keeper.setDaemon(false);
            keeper.start();
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        running.set(false);
        if (keeper != null) {
            keeper.interrupt();
        }
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // Start late, stop late
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
