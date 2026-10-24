package io.github.lgp547.anydoorplugin.result;

import java.time.Instant;

/** Immutable snapshot displayed by the result viewer. */
public record InvocationResult(String id, String invocation, String target, Status status,
                               long startedAt, long completedAt, String json, String error) {
    public enum Status { RUNNING, SUCCESS, ERROR }

    public static InvocationResult running(String id, String invocation, String target) {
        return new InvocationResult(id, invocation, target, Status.RUNNING, Instant.now().toEpochMilli(), 0, null, null);
    }

    public InvocationResult success(String value) {
        return new InvocationResult(id, invocation, target, Status.SUCCESS, startedAt,
                Instant.now().toEpochMilli(), value == null || value.isBlank() ? "null" : value, null);
    }

    public InvocationResult failure(Throwable failure) {
        String message = failure == null ? "Unknown invocation error" : failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getName();
        return new InvocationResult(id, invocation, target, Status.ERROR, startedAt,
                Instant.now().toEpochMilli(), null, message);
    }

    public long durationMillis() {
        return (completedAt == 0 ? Instant.now().toEpochMilli() : completedAt) - startedAt;
    }
}
