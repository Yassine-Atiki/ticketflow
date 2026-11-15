package com.ticketflow.mcptoolserver.exceptions;

public record ToolFailure(
        String code,
        String service,
        String operation,
        String message,
        Integer httpStatus) {

    public static ToolFailure invalid(String message) {
        return new ToolFailure("INVALID_ARGUMENTS", null, null, message, null);
    }

    public static ToolFailure confirmationRequired(String operation) {
        return new ToolFailure(
                "CONFIRMATION_REQUIRED",
                null,
                operation,
                "The operation was not executed. Ask the user to confirm "
                        + operation
                        + " explicitly, then call the tool again with userConfirmed set to true.",
                null);
    }

    public static ToolFailure contractNotConfirmed(String service, String operation) {
        return new ToolFailure(
                "CONTRACT_NOT_CONFIRMED",
                service,
                operation,
                service + " operation " + operation
                        + " is disabled. The call was not sent. Enable contract-confirmed only after that service implements the configured path.",
                null);
    }

    public static ToolFailure serviceNotConfigured(String service, String operation) {
        return new ToolFailure(
                "SERVICE_NOT_CONFIGURED",
                service,
                operation,
                service + " URL is not configured, so the call was not sent.",
                null);
    }

    public static ToolFailure misconfigured(String service, String operation, String message) {
        return new ToolFailure("MISCONFIGURED", service, operation, message, null);
    }

    public static ToolFailure authenticationFailed(String service, String operation, int httpStatus) {
        return new ToolFailure(
                "AUTHENTICATION_FAILED",
                service,
                operation,
                service + " rejected the caller credentials (HTTP " + httpStatus + ").",
                httpStatus);
    }

    public static ToolFailure callerAuthenticationRequired(String service, String operation) {
        return new ToolFailure(
                "AUTHENTICATION_FAILED",
                service,
                operation,
                service + " identifies the caller from the bearer token. No Authorization header was present, so the call was not sent.",
                null);
    }

    public static ToolFailure timeout(String service, String operation) {
        return new ToolFailure(
                "DOWNSTREAM_TIMEOUT",
                service,
                operation,
                service + " did not respond before the configured timeout.",
                null);
    }

    public static ToolFailure unavailable(String service, String operation) {
        return new ToolFailure(
                "DOWNSTREAM_UNAVAILABLE",
                service,
                operation,
                service + " is unavailable.",
                null);
    }

    public static ToolFailure httpError(String service, String operation, int httpStatus, String detail) {
        return new ToolFailure("DOWNSTREAM_HTTP_ERROR", service, operation, detail, httpStatus);
    }

    public static ToolFailure unexpectedResponse(String service, String operation, String message) {
        return new ToolFailure("UNEXPECTED_RESPONSE", service, operation, message, null);
    }

    public static ToolFailure unexpected(String operation) {
        return new ToolFailure(
                "UNEXPECTED_ERROR",
                null,
                operation,
                "The tool failed unexpectedly. The operation was not reported as successful.",
                null);
    }
}
