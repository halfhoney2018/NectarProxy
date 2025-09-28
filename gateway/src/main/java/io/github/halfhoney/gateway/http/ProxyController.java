package io.github.halfhoney.gateway.http;

import com.achance.gateway.common.dto.ProxyRequest;
import com.achance.gateway.common.dto.ProxyResponse;
import io.github.halfhoney.gateway.rsocket.ClientRegistry;
import io.github.halfhoney.gateway.config.GatewayProxyProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/proxy")
public class ProxyController {
    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);
    private final ClientRegistry registry;
    private final GatewayProxyProperties props;

    public ProxyController(ClientRegistry registry, GatewayProxyProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @RequestMapping(path = "/{clientId}/**")
    public Mono<ResponseEntity<byte[]>> proxy(@PathVariable String clientId,
                                              ServerWebExchange exchange,
                                              @RequestBody(required = false) Mono<byte[]> bodyMono) {
        long start = System.currentTimeMillis();
        RSocketRequester requester = registry.get(clientId);
        if (requester == null) {
            log.warn("Proxy request rejected: clientId={} not connected", clientId);
            return Mono.just(ResponseEntity.status(503).body((byte[]) null));
        }
        String fullPath = exchange.getRequest().getURI().getPath();
        String prefix = "/proxy/" + clientId;
        String targetPath = fullPath.substring(prefix.length());
        if (targetPath.isEmpty()) targetPath = "/";

        ProxyRequest pr = new ProxyRequest();
        pr.setRequestId(UUID.randomUUID().toString());
        pr.setMethod(exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "GET");
        pr.setPath(targetPath);
        pr.setQuery(exchange.getRequest().getQueryParams().toSingleValueMap());
        pr.setHeaders(headersToMap(exchange.getRequest().getHeaders()));
        // 计算超时：请求头 > client 策略 > 全局默认
        Integer timeoutMs = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Timeout-Ms"))
                .map(Integer::valueOf)
                .orElseGet(() -> {
                    GatewayProxyProperties.ClientPolicy p = props.getClients() != null ? props.getClients().get(clientId) : null;
                    return p != null && p.getTimeoutMs() != null ? p.getTimeoutMs() : props.getDefaultTimeoutMs();
                });
        pr.setTimeoutMs(timeoutMs);

        // 目标地址选择：client.routes(按顺序匹配prefix) > client.defaultTargetBaseUrl > global.defaultTargetBaseUrl
        GatewayProxyProperties.ClientPolicy policy0 = props.getClients() != null ? props.getClients().get(clientId) : null;
        String targetBaseUrl = null;
        if (policy0 != null && policy0.getRoutes() != null) {
            for (GatewayProxyProperties.Route r : policy0.getRoutes()) {
                if (r.getPrefix() != null && pr.getPath().startsWith(r.getPrefix())) {
                    targetBaseUrl = r.getTargetBaseUrl();
                    break;
                }
            }
        }
        if (targetBaseUrl == null && policy0 != null) {
            targetBaseUrl = policy0.getDefaultTargetBaseUrl();
        }
        if (targetBaseUrl == null) {
            targetBaseUrl = props.getDefaultTargetBaseUrl();
        }
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            log.warn("No targetBaseUrl resolved for clientId={}, path={}", clientId, pr.getPath());
            return Mono.just(ResponseEntity.status(502).body((byte[]) null));
        }
        pr.setTargetBaseUrl(targetBaseUrl);

        Mono<ProxyRequest> dataMono = bodyMono
                .defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    if (bytes.length > 0) {
                        pr.setBodyBase64(Base64.getEncoder().encodeToString(bytes));
                    }
                    return pr;
                });

        // ACL 校验（可选）
        GatewayProxyProperties.ClientPolicy policy = policy0;
        if (policy != null && Boolean.FALSE.equals(policy.getEnabled())) {
            log.warn("Proxy request denied: clientId={} disabled by policy", clientId);
            return Mono.just(ResponseEntity.status(403).body((byte[]) null));
        }
        if (policy != null && policy.getAllowedMethods() != null && !policy.getAllowedMethods().isEmpty()) {
            String m = pr.getMethod();
            if (policy.getAllowedMethods().stream().noneMatch(allow -> allow.equalsIgnoreCase(m))) {
                log.warn("Proxy request denied: clientId={}, method={} not allowed", clientId, m);
                return Mono.just(ResponseEntity.status(403).body((byte[]) null));
            }
        }
        if (policy != null && policy.getAllowedPathPrefixes() != null && !policy.getAllowedPathPrefixes().isEmpty()) {
            String tp = pr.getPath();
            boolean ok = policy.getAllowedPathPrefixes().stream().anyMatch(tp::startsWith);
            if (!ok) {
                log.warn("Proxy request denied: clientId={}, path={} not allowed", clientId, tp);
                return Mono.just(ResponseEntity.status(403).body((byte[]) null));
            }
        }

        log.debug("Proxy dispatch -> clientId={}, method={}, path={}, reqId={}, timeoutMs={}", clientId, pr.getMethod(), pr.getPath(), pr.getRequestId(), timeoutMs);

        return dataMono
                .flatMap(data -> requester.route("agent.proxy.request").data(data).retrieveMono(ProxyResponse.class))
                .map(resp -> {
                    long cost = System.currentTimeMillis() - start;
                    log.info("Proxy response <- clientId={}, status={}, cost={}ms, reqId={}", clientId, resp.getStatus(), cost, pr.getRequestId());
                    return toHttpResponse(resp);
                })
                .doOnError(ex -> {
                    long cost = System.currentTimeMillis() - start;
                    log.error("Proxy error <- clientId={}, cost={}ms, reqId={}, err={}", clientId, cost, pr.getRequestId(), ex.toString());
                });
    }

    private Map<String, String> headersToMap(HttpHeaders headers) {
        Map<String, String> map = new HashMap<>();
        headers.forEach((k, v) -> map.put(k, String.join(",", v)));
        return map;
    }

    private ResponseEntity<byte[]> toHttpResponse(ProxyResponse resp) {
        HttpHeaders headers = new HttpHeaders();
        if (resp.getHeaders() != null) resp.getHeaders().forEach(headers::add);
        byte[] body = null;
        if (resp.getBodyBase64() != null) body = Base64.getDecoder().decode(resp.getBodyBase64());
        return ResponseEntity.status(resp.getStatus()).headers(headers).body(body);
    }
}
