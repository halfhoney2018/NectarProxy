package com.achance.gateway.agent.rsocket;

import com.achance.gateway.common.dto.ProxyRequest;
import com.achance.gateway.common.dto.ProxyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.achance.gateway.agent.config.AgentProperties;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.messaging.handler.annotation.MessageMapping;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Controller
public class AgentHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentHandler.class);
    private final WebClient.Builder webClientBuilder;
    private final AgentProperties props;

    public AgentHandler(WebClient.Builder webClientBuilder, AgentProperties props) {
        this.webClientBuilder = webClientBuilder;
        this.props = props;
    }

    @MessageMapping("agent.proxy.request")
    public Mono<ProxyResponse> handle(ProxyRequest req) {
        long start = System.currentTimeMillis();
        String method = req.getMethod();
        String path = req.getPath();
        log.debug("Agent handle -> method={}, path={}, reqId={}", method, path, req.getRequestId());

        // 仅使用网关下发的目标地址；若缺失则返回错误
        String baseUrl = req.getTargetBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Missing targetBaseUrl from gateway for reqId={}, path={}", req.getRequestId(), path);
            ProxyResponse resp = new ProxyResponse();
            resp.setRequestId(req.getRequestId());
            resp.setStatus(502);
            ProxyResponse.ErrorBody err = new ProxyResponse.ErrorBody();
            err.setCode("TARGET_NOT_RESOLVED");
            err.setMessage("No targetBaseUrl provided by gateway");
            resp.setError(err);
            return Mono.just(resp);
        }
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        WebClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method))
                .uri(uriBuilder -> uriBuilder.path(path).build());
        if (req.getHeaders() != null) {
            for (Map.Entry<String, String> e : req.getHeaders().entrySet()) {
                spec.header(e.getKey(), e.getValue());
            }
        }
        Mono<byte[]> bodyMono = Mono.justOrEmpty(req.getBodyBase64())
                .map(s -> Base64.getDecoder().decode(s));

        // 透明透传：使用 exchangeToMono 获取状态码与头部，无论 2xx/4xx/5xx 都构造 ProxyResponse
        Mono<ProxyResponse> respMono = ((req.getBodyBase64() != null)
                ? spec.body(BodyInserters.fromPublisher(bodyMono, byte[].class))
                : spec)
                .exchangeToMono(clientResp -> clientResp
                        .bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(bytes -> {
                            long cost = System.currentTimeMillis() - start;
                            int status = clientResp.rawStatusCode();
                            log.info("Local HTTP response <- {} {}, status={}, bytes={}, cost={}ms, reqId={}",
                                    method, path, status, bytes.length, cost, req.getRequestId());
                            ProxyResponse resp = new ProxyResponse();
                            resp.setRequestId(req.getRequestId());
                            resp.setStatus(status);
                            // 拷贝下游响应头
                            Map<String, String> hdrs = new java.util.HashMap<>();
                            clientResp.headers().asHttpHeaders().forEach((k, v) -> hdrs.put(k, String.join(",", v)));
                            resp.setHeaders(hdrs);
                            if (bytes.length > 0) {
                                resp.setBodyBase64(Base64.getEncoder().encodeToString(bytes));
                            }
                            return resp;
                        })
                );

        int timeout = (req.getTimeoutMs() != null && req.getTimeoutMs() > 0) ? req.getTimeoutMs() : props.getDefaultTimeoutMs();

        return respMono
                .doOnSubscribe(s -> log.debug("Local HTTP dispatch -> {} {}", method, path))
                .onErrorResume(ex -> {
                    long cost = System.currentTimeMillis() - start;
                    log.error("Local HTTP transport error <- {} {}, cost={}ms, reqId={}, err={}",
                            method, path, cost, req.getRequestId(), ex.toString());
                    ProxyResponse resp = new ProxyResponse();
                    resp.setRequestId(req.getRequestId());
                    resp.setStatus(502);
                    ProxyResponse.ErrorBody err = new ProxyResponse.ErrorBody();
                    err.setCode("DOWNSTREAM_UNAVAILABLE");
                    err.setMessage(ex.getMessage());
                    resp.setError(err);
                    return Mono.just(resp);
                });
    }

    // 网关统一配置后，不再在 Agent 侧进行前缀路由匹配
}
