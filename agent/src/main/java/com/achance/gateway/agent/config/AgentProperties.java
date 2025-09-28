package com.achance.gateway.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    /** 唯一客户端ID（用于注册到网关） */
    private String clientId = "client-001";

    /** 网关 RSocket WebSocket 地址，如 ws://localhost:9000/rsocket */
    private String gatewayUrl = "ws://localhost:9000/rsocket";

    /** 本地目标基础URL（Agent 将把请求转发到这里） */
    private String targetBaseUrl = "http://127.0.0.1:9000";

    /** 默认超时（毫秒） */
    private int defaultTimeoutMs = 30_000;

    /** 可选的前缀路由映射，优先匹配。按顺序匹配第一个前缀。 */
    private List<Route> routes;

    @Getter
    @Setter
    public static class Route {
        /** 以 / 开头的路径前缀，例如 /open 或 /api */
        private String prefix;
        /** 命中的目标基础URL，例如 http://127.0.0.1:7001 */
        private String targetBaseUrl;
    }
}
