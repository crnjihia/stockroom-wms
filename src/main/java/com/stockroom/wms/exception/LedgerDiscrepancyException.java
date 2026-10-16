package com.stockroom.wms.exception;

public class LedgerDiscrepancyException extends RuntimeException {
    public LedgerDiscrepancyException(String message) {
        super(message);
    }
}
