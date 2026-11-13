package com.littlehorizons.erp.exception;

/** Unchecked wrapper for database failures; the technical detail goes to the error log. */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}