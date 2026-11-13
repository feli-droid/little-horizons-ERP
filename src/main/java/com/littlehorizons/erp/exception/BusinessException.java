package com.littlehorizons.erp.exception;

/** Signals that a business rule or a validation was not satisfied. */
public class BusinessException extends Exception {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }
}