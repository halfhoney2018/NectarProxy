package com.achance.gateway.common.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ProxyResponse {
    private String requestId;
    private int status;
    private Map<String, String> headers;
    private String bodyBase64;
    private ErrorBody error;

    @Data
    public static class ErrorBody {
        private String code;
        private String message;
        private Map<String, Object> details;
    }
}
