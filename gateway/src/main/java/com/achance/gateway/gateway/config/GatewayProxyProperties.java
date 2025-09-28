package com.achance.gateway.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway.proxy")
public class GatewayProxyProperties {
    /** 入口前缀，如 /proxy */
    private String routePrefix = "/proxy";

    /** 默认超时（毫秒） */
    private int defaultTimeoutMs = 30_000;

    /** 全局默认目标基础URL（未命中任何路由与客户端默认时使用） */
    private String defaultTargetBaseUrl;

    /** 按 clientId 的策略配置（可选） */
    private Map<String, ClientPolicy> clients;

    @Getter
    @Setter
    public static class ClientPolicy {
        private Boolean enabled = true;
        private List<String> allowedMethods;      // 允许的方法，null 表示不限制
        private List<String> allowedPathPrefixes; // 允许的路径前缀，例如 /open
        private Integer timeoutMs;                // 覆盖默认超时
        /** 该 clientId 的默认目标基础URL（未命中 routes 时使用） */
        private String defaultTargetBaseUrl;
        /** 路由表：按顺序匹配 prefix，命中后使用对应 targetBaseUrl */
        private List<Route> routes;
    }

    @Getter
    @Setter
    public static class Route {
        private String prefix;         // 以 / 开头的路径前缀
        private String targetBaseUrl;  // 命中的目标基础URL
    }
}
