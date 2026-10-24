package io.github.lgp547.anydoorplugin.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvocationResultCodecTest {
    @Test void preservesStructuredAttachResults() {
        InvocationResultCodec.Decoded decoded = InvocationResultCodec.decodeAttach(
                "{\"status\":\"success\",\"time\":1,\"resultJson\":{\"items\":[1,null],\"ok\":true}}");
        assertNull(decoded.error());
        assertTrue(decoded.json().contains("\"items\": ["));
        assertTrue(decoded.json().contains("null"));
    }

    @Test void preservesBusinessStringsAsJsonStrings() {
        assertEquals("\"plain text\"", InvocationResultCodec.decodeAttach(
                "{\"status\":\"success\",\"resultJson\":\"plain text\"}").json());
        assertEquals("\"plain text\"", InvocationResultCodec.prettyResponse("\"plain text\""));
    }

    @Test void exposesRemoteErrors() {
        InvocationResultCodec.Decoded decoded = InvocationResultCodec.decodeAttach(
                "{\"status\":\"error\",\"error\":\"boom\"}");
        assertNull(decoded.json());
        assertEquals("boom", decoded.error());
    }

    @Test void remainsCompatibleWithLegacyStringEnvelopes() {
        InvocationResultCodec.Decoded decoded = InvocationResultCodec.decodeAttach(
                "{\"status\":\"success\",\"result\":\"{\\\"id\\\":7}\"}");
        assertTrue(decoded.json().contains("\"id\": 7"));
    }
}
