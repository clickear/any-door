package io.github.lgp547.anydoorplugin.request;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class AnyDoorSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey TITLE=TextAttributesKey.createTextAttributesKey("ANYDOOR_TITLE", DefaultLanguageHighlighterColors.METADATA);
    public static final TextAttributesKey DIRECTIVE=TextAttributesKey.createTextAttributesKey("ANYDOOR_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey DIRECTIVE_NAME=TextAttributesKey.createTextAttributesKey("ANYDOOR_DIRECTIVE_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey RUN=TextAttributesKey.createTextAttributesKey("ANYDOOR_RUN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey CLASS=TextAttributesKey.createTextAttributesKey("ANYDOOR_CLASS", DefaultLanguageHighlighterColors.CLASS_REFERENCE);
    public static final TextAttributesKey METHOD=TextAttributesKey.createTextAttributesKey("ANYDOOR_METHOD", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey TYPE=TextAttributesKey.createTextAttributesKey("ANYDOOR_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey VARIABLE=TextAttributesKey.createTextAttributesKey("ANYDOOR_VARIABLE", DefaultLanguageHighlighterColors.MARKUP_TAG);
    public static final TextAttributesKey COMMENT=TextAttributesKey.createTextAttributesKey("ANYDOOR_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey JSON_KEY=TextAttributesKey.createTextAttributesKey("ANYDOOR_JSON_KEY", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey JSON_STRING=TextAttributesKey.createTextAttributesKey("ANYDOOR_JSON_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey JSON_NUMBER=TextAttributesKey.createTextAttributesKey("ANYDOOR_JSON_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    @Override public @NotNull Lexer getHighlightingLexer(){return new AnyDoorLexer();}
    @Override public TextAttributesKey @NotNull [] getTokenHighlights(IElementType token){if(token==AnyDoorTokenTypes.TITLE)return pack(TITLE);if(token==AnyDoorTokenTypes.DIRECTIVE)return pack(DIRECTIVE);if(token==AnyDoorTokenTypes.DIRECTIVE_NAME)return pack(DIRECTIVE_NAME);if(token==AnyDoorTokenTypes.ASSIGNMENT)return pack(DefaultLanguageHighlighterColors.OPERATION_SIGN);if(token==AnyDoorTokenTypes.RUN)return pack(RUN);if(token==AnyDoorTokenTypes.CLASS_REFERENCE)return pack(CLASS);if(token==AnyDoorTokenTypes.METHOD_REFERENCE)return pack(METHOD);if(token==AnyDoorTokenTypes.TYPE_REFERENCE)return pack(TYPE);if(token==AnyDoorTokenTypes.TYPE_PARENTHESIS)return pack(DefaultLanguageHighlighterColors.PARENTHESES);if(token==AnyDoorTokenTypes.TYPE_SEPARATOR)return pack(DefaultLanguageHighlighterColors.COMMA);if(token==AnyDoorTokenTypes.VARIABLE)return pack(VARIABLE);if(token==AnyDoorTokenTypes.JSON_KEY)return pack(JSON_KEY);if(token==AnyDoorTokenTypes.JSON_STRING)return pack(JSON_STRING);if(token==AnyDoorTokenTypes.JSON_NUMBER)return pack(JSON_NUMBER);if(token==AnyDoorTokenTypes.JSON_BOOLEAN||token==AnyDoorTokenTypes.JSON_NULL)return pack(DefaultLanguageHighlighterColors.KEYWORD);if(token==AnyDoorTokenTypes.JSON_PUNCTUATION)return pack(DefaultLanguageHighlighterColors.BRACES);if(token==AnyDoorTokenTypes.COMMENT)return pack(COMMENT);return pack(HighlighterColors.TEXT);}
}
