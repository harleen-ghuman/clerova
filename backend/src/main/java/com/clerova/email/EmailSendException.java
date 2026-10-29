package com.clerova.email;

public class EmailSendException extends RuntimeException {

    /** The provider answered and refused the email, so nothing was sent. */
    private final boolean rejected;

    public EmailSendException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public EmailSendException(String message, Throwable cause, boolean rejected) {
        super(message, cause);
        this.rejected = rejected;
    }

    public boolean isRejected() {
        return rejected;
    }
}
