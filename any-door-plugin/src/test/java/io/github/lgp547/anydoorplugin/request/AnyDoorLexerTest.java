package io.github.lgp547.anydoorplugin.request;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnyDoorLexerTest {
    @Test void keepsRunReferencesJsonAndCommentSeparate() {
        AnyDoorLexer lexer = new AnyDoorLexer();
        String source = "RUN demo.UserService#create(UserCmd) // note\n{\"id\":1,\"active\":true,\"name\":\"{{name}}\"}";
        lexer.start(source, 0, source.length(), 0);
        List<Object> types = new ArrayList<>();
        while (lexer.getTokenType() != null) { types.add(lexer.getTokenType()); lexer.advance(); }
        assertTrue(types.contains(AnyDoorTokenTypes.CLASS_REFERENCE));
        assertTrue(types.contains(AnyDoorTokenTypes.METHOD_REFERENCE));
        assertTrue(types.contains(AnyDoorTokenTypes.TYPE_REFERENCE));
        assertTrue(types.contains(AnyDoorTokenTypes.TYPE_PARENTHESIS));
        assertTrue(types.contains(AnyDoorTokenTypes.COMMENT));
        assertTrue(types.contains(AnyDoorTokenTypes.JSON_KEY));
        assertTrue(types.contains(AnyDoorTokenTypes.JSON_NUMBER));
        assertTrue(types.contains(AnyDoorTokenTypes.VARIABLE));
    }
    @Test void separatesMethodParameterPunctuationFromTypes() {
        AnyDoorLexer lexer = new AnyDoorLexer();
        String source = "RUN demo.UserService#create(UserCmd, String)";
        lexer.start(source, 0, source.length(), 0);
        List<Object> types = new ArrayList<>();
        List<String> values = new ArrayList<>();
        while (lexer.getTokenType() != null) { types.add(lexer.getTokenType()); values.add(source.substring(lexer.getTokenStart(), lexer.getTokenEnd())); lexer.advance(); }
        assertEquals(2, types.stream().filter(type -> type == AnyDoorTokenTypes.TYPE_PARENTHESIS).count());
        assertTrue(types.contains(AnyDoorTokenTypes.TYPE_SEPARATOR));
        assertEquals("UserCmd", values.get(types.indexOf(AnyDoorTokenTypes.TYPE_REFERENCE)));
    }
    @Test void tokenizesTemplateInsideJsonStringWithoutColouringQuotesAsVariable() {
        AnyDoorLexer lexer = new AnyDoorLexer();
        String source = "{\"name\": \"hello {{userName}}\"}";
        lexer.start(source, 0, source.length(), 0);
        List<Object> types = new ArrayList<>();
        List<String> values = new ArrayList<>();
        while (lexer.getTokenType() != null) { types.add(lexer.getTokenType()); values.add(source.substring(lexer.getTokenStart(), lexer.getTokenEnd())); lexer.advance(); }
        int variable = types.indexOf(AnyDoorTokenTypes.VARIABLE);
        assertTrue(variable >= 0);
        assertEquals("{{userName}}", values.get(variable));
        assertTrue(types.contains(AnyDoorTokenTypes.JSON_STRING));
    }
}
