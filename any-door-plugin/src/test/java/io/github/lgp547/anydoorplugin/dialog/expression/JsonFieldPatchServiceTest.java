package io.github.lgp547.anydoorplugin.dialog.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonFieldPatchServiceTest {

    @Test
    void patchesOnlyTargetLeafField() throws Exception {
        String json = """
                {
                  "user": {
                    "address": {
                      "city": "Beijing",
                      "zip": "100000"
                    }
                  }
                }
                """;

        String patched = new JsonFieldPatchService().patch(json, "user.address.city", "\"Shanghai\"");

        JsonNode actual = new ObjectMapper().readTree(patched);
        JsonNode expected = new ObjectMapper().readTree("""
                {
                  "user": {
                    "address": {
                      "city": "Shanghai",
                      "zip": "100000"
                    }
                  }
                }
                """);
        assertEquals(expected, actual);
    }
}
