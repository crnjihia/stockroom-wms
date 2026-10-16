package com.stockroom.wms.exception;

public class OptimisticLockRetryExhaustedException extends RuntimeException {
    public OptimisticLockRetryExhaustedException(String message) {
        super(message);
    }

    public OptimisticLockRetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
