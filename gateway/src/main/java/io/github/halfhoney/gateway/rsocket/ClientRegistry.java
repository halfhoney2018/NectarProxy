package io.github.halfhoney.gateway.rsocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClientRegistry {
    private static final Logger log = LoggerFactory.getLogger(ClientRegistry.class);
    private final Map<String, RSocketRequester> clients = new ConcurrentHashMap<>();

    public void register(String clientId, RSocketRequester requester) {
        clients.put(clientId, requester);
        log.info("RSocket client registered: clientId={}", clientId);
        requester.rsocket().onClose()
                .doFirst(() -> log.debug("onClose subscribed for clientId={}", clientId))
                .doFinally(s -> {
                    clients.remove(clientId);
                    log.warn("RSocket client disconnected: clientId={}, signal={}", clientId, s);
                })
                .subscribe();
    }

    public RSocketRequester get(String clientId) {
        RSocketRequester r = clients.get(clientId);
        if (r == null) {
            log.warn("RSocket client not found: clientId={}", clientId);
        }
        return r;
    }
}
