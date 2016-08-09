package com.tradeshift.reaktive.marshal;

@SuppressWarnings("serial")
public class ValidationException extends RuntimeException {

    public ValidationException() {
        super();
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(Throwable cause) {
        super(cause);
    }
}
