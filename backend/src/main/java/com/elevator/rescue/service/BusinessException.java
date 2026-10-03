package com.elevator.rescue.service;

/** 业务规则冲突（交接缺项、未经复检恢复载客等），映射为 HTTP 409 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
