package com.elevator.rescue.service;

import java.util.List;

/** 业务异常，携带 HTTP 状态码与可选的明细（如缺失的交接项） */
public class ApiException extends RuntimeException {

    private final int status;
    private final List<String> details;

    public ApiException(int status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message, List.of());
    }

    public static ApiException badRequest(String message, List<String> details) {
        return new ApiException(400, message, details);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message, List.of());
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message, List.of());
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message, List.of());
    }

    public int getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}
