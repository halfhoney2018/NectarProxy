package com.achance.gateway.agent.rsocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.achance.gateway.agent.config.AgentProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;

@Component
public class AgentRegisterRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRegisterRunner.class);
    private final RSocketRequester requester;
    private final AgentProperties props;

    public AgentRegisterRunner(RSocketRequester requester, AgentProperties props) {
        this.requester = requester;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        String clientId = props.getClientId();
        log.info("Agent registering to gateway, clientId={}", clientId);
        requester.route("register").data(clientId).send()
                .doOnSubscribe(s -> log.debug("Send register started: clientId={}", clientId))
                .doOnSuccess(v -> log.info("Register sent successfully: clientId={}", clientId))
                .doOnError(ex -> log.error("Register send failed: clientId={}, err={}", clientId, ex.toString()))
                .subscribe();
    }
}
