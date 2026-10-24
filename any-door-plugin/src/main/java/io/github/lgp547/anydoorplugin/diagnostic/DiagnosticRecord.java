package io.github.lgp547.anydoorplugin.diagnostic;

import com.google.gson.JsonObject;
import io.github.lgp547.anydoor.common.dto.AnyDoorDiagnosticReport;

public record DiagnosticRecord(String id, String invocation, String target, long startedAt, State state,
                               JsonObject request, AnyDoorDiagnosticReport report, String transportError) {
    public enum State { RUNNING, PASS, WARN, FAIL }
    public DiagnosticRecord complete(AnyDoorDiagnosticReport value) {
        State next = "FAIL".equals(value.getOverallStatus()) ? State.FAIL : "WARN".equals(value.getOverallStatus()) ? State.WARN : State.PASS;
        return new DiagnosticRecord(id, invocation, target, startedAt, next, request, value, null);
    }
    public DiagnosticRecord failure(String error, AnyDoorDiagnosticReport value) {
        return new DiagnosticRecord(id, invocation, target, startedAt, State.FAIL, request, value, error);
    }
}
