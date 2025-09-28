package io.github.halfhoney.gateway.rsocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Controller;

@Controller
public class GatewayRSocketController {
    private static final Logger log = LoggerFactory.getLogger(GatewayRSocketController.class);
    private final ClientRegistry registry;

    public GatewayRSocketController(ClientRegistry registry) {
        this.registry = registry;
    }

    @MessageMapping("register")
    public void register(String clientId, RSocketRequester requester) {
        log.info("Received register from clientId={}", clientId);
        registry.register(clientId, requester);
        log.debug("Client {} registered and requester stored", clientId);
    }
}
