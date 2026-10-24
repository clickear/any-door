package io.github.lgp547.anydoorplugin.diagnostic;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticProtocolTest {
    @Test void rejectsLegacyAndInvalidResponsesClearly() {
        IllegalStateException legacy = assertThrows(IllegalStateException.class,
                () -> InvocationDiagnosticService.decodeReport("{\"status\":\"success\"}"));
        assertTrue(legacy.getMessage().contains("does not support"));
        assertThrows(IllegalStateException.class, () -> InvocationDiagnosticService.decodeReport("not-json"));
    }

    @Test void acceptsVersionedReports() {
        var report = InvocationDiagnosticService.decodeReport("{\"protocolVersion\":\"1\",\"stages\":[],\"overallStatus\":\"PASS\"}");
        assertEquals("1", report.getProtocolVersion());
        assertEquals("PASS", report.getOverallStatus());
    }

    @Test void sanitizerRemovesSecretsAndUserDirectoriesAndTruncates() {
        String sanitized = DiagnosticSanitizer.text("password=secret /home/alice/project C:\\Users\\bob\\project token:abc");
        assertFalse(sanitized.contains("secret")); assertFalse(sanitized.contains("alice")); assertFalse(sanitized.contains("bob")); assertFalse(sanitized.contains("abc"));
        assertTrue(sanitized.contains("<redacted>")); assertTrue(DiagnosticSanitizer.text("x".repeat(50000)).endsWith("truncated"));
    }

    @Test void diagnosticRequestCannotBeMistakenForBusinessInvocationByOldAgent() {
        var request = JsonParser.parseString("{\"className\":\"demo.Service\",\"methodName\":\"charge\",\"parameterTypes\":[\"java.lang.String\"]}").getAsJsonObject();
        InvocationDiagnosticService.prepareDiagnosticRequest(request);
        assertFalse(request.has("methodName")); assertFalse(request.has("parameterTypes"));
        assertEquals("charge", request.get("diagnosticMethodName").getAsString());
        assertEquals("diagnose", request.get("operation").getAsString());
    }

    @Test void exceptionSanitizerRemovesActualRequestValues() {
        var request = JsonParser.parseString("{\"content\":\"{\\\"account\\\":\\\"customer-12345\\\"}\"}").getAsJsonObject();
        String value = DiagnosticSanitizer.exception(new IllegalStateException("failed customer-12345"), request);
        assertFalse(value.contains("customer-12345")); assertTrue(value.contains("<parameter>"));
    }
}
