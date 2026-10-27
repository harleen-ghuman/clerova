package com.clerova.exception;

/**
 * A request that can't be done in the item's current state. Its message is
 * written for the person using Clerova and is shown as is.
 */
public class ConflictException extends IllegalStateException {

    public ConflictException(String message) {
        super(message);
    }
}
