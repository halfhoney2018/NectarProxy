package com.achance.gateway.common.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ProxyRequest {
    private String requestId;
    private String method;
    private String path;
    private Map<String, String> query;
    private Map<String, String> headers;
    private String bodyBase64;
    private Integer timeoutMs;
    /** 由网关下发的目标基础URL（Agent据此转发），例如 http://127.0.0.1:9000 或 https://httpbin.org */
    private String targetBaseUrl;
}
