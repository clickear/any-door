package io.github.lgp547.anydoorplugin.dialog.expression;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldSelectionResolverTest {

    @Test
    void resolvesLeafFieldFromCaretInsideValue() {
        String json = """
                {
                  "user": {
                    "address": {
                      "city": "Beijing"
                    }
                  }
                }
                """;

        int caret = json.indexOf("Beijing") + 2;

        Optional<FieldTarget> target = new FieldSelectionResolver().resolveFromJsonCaret(
                "user",
                "java.lang.String",
                json,
                caret
        );

        assertTrue(target.isPresent());
        assertEquals("user.address.city", target.get().jsonPath());
    }

    @Test
    void returnsEmptyWhenCaretIsAtRootWhitespace() {
        String json = """
                {
                  "user": {
                    "name": "Alice"
                  }
                }
                """;

        int caret = json.indexOf("{");

        Optional<FieldTarget> target = new FieldSelectionResolver().resolveFromJsonCaret(
                "user",
                "java.lang.String",
                json,
                caret
        );

        assertTrue(target.isEmpty());
    }

    @Test
    void resolvesNestedSiblingWithoutConfusingSameLevelKeys() {
        String json = """
                {
                  "user": {
                    "name": "Alice",
                    "address": {
                      "city": "Beijing",
                      "zip": "100000"
                    }
                  }
                }
                """;

        int caret = json.indexOf("100000") + 2;

        Optional<FieldTarget> target = new FieldSelectionResolver().resolveFromJsonCaret(
                "user",
                "java.lang.String",
                json,
                caret
        );

        assertTrue(target.isPresent());
        assertEquals("user.address.zip", target.get().jsonPath());
    }
}
