package com.achance.gateway.agent.config;

import com.achance.gateway.agent.rsocket.AgentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import io.rsocket.metadata.WellKnownMimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Configuration
public class AgentRSocketConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentRSocketConfig.class);

    @Bean
    public RSocketRequester rSocketRequester(RSocketRequester.Builder builder,
                                             RSocketStrategies strategies,
                                             AgentProperties props,
                                             AgentHandler agentHandler) {
        var uri = UriComponentsBuilder.fromUriString(props.getGatewayUrl()).build().toUri();
        log.info("Connecting RSocket to {} ...", uri);

        // Configure client-side responder so gateway can invoke @MessageMapping on Agent
        RSocketMessageHandler messageHandler = new RSocketMessageHandler();
        messageHandler.setRSocketStrategies(strategies);
        messageHandler.setHandlers(List.of(agentHandler));
        try {
            messageHandler.afterPropertiesSet();
        } catch (Exception e) {
            log.error("Failed to initialize RSocketMessageHandler: {}", e.toString());
        }
        if (log.isDebugEnabled()) {
            var keys = messageHandler.getHandlerMethods().keySet();
            log.debug("RSocket handler destinations mapped: {}", keys);
        }

        RSocketRequester requester = connect(builder, strategies, messageHandler, uri)
                .doOnSuccess(r -> log.info("RSocket connected to {}", uri))
                .block();

        if (requester != null) {
            attachOnClose(requester, builder, strategies, messageHandler, uri, props);
        }
        return requester;
    }

    private Mono<RSocketRequester> connect(RSocketRequester.Builder builder,
                                           RSocketStrategies strategies,
                                           RSocketMessageHandler messageHandler,
                                           java.net.URI uri) {
        return builder
                .dataMimeType(MimeTypeUtils.APPLICATION_JSON)
                .metadataMimeType(MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()))
                .rsocketStrategies(strategies)
                .rsocketConnector(connector -> connector.acceptor(messageHandler.responder()))
                .connectWebSocket(uri)
                .doOnSubscribe(s -> log.debug("RSocket connect subscribe -> {}", uri))
                .doOnError(ex -> log.error("RSocket connect failed to {}: {}", uri, ex.toString()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .transientErrors(true)
                        .doBeforeRetry(rs -> log.warn("RSocket reconnect attempt #{} to {} (reason: {})",
                                rs.totalRetries() + 1, uri, rs.failure().toString())));
    }

    private void attachOnClose(RSocketRequester req,
                               RSocketRequester.Builder builder,
                               RSocketStrategies strategies,
                               RSocketMessageHandler messageHandler,
                               java.net.URI uri,
                               AgentProperties props) {
        req.rsocket().onClose()
                .doFirst(() -> log.debug("onClose subscribed for {}", uri))
                .doFinally(signal -> {
                    log.warn("RSocket disconnected from {}: signal={}", uri, signal);
                    connect(builder, strategies, messageHandler, uri)
                            .doOnSuccess(newReq -> {
                                log.info("RSocket reconnected to {}", uri);
                                newReq.route("register").data(props.getClientId()).send()
                                        .doOnSuccess(v -> log.info("Re-register sent successfully: clientId={}", props.getClientId()))
                                        .doOnError(ex -> log.error("Re-register failed: clientId={}, err={}", props.getClientId(), ex.toString()))
                                        .subscribe();
                                // recursively attach for subsequent disconnects
                                attachOnClose(newReq, builder, strategies, messageHandler, uri, props);
                            })
                            .subscribe();
                })
                .subscribe();
    }
}
