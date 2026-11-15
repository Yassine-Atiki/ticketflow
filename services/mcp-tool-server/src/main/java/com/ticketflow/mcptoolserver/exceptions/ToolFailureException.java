package com.ticketflow.mcptoolserver.exceptions;

public class ToolFailureException extends RuntimeException {

    private final ToolFailure failure;

    public ToolFailureException(ToolFailure failure) {
        super(failure.message());
        this.failure = failure;
    }

    public ToolFailure failure() {
        return failure;
    }
}
