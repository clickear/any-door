package io.github.lgp547.anydoorplugin.request;

import com.intellij.psi.tree.IElementType;

public final class AnyDoorTokenTypes {
    public static final IElementType TITLE = new IElementType("TITLE", AnyDoorLanguage.INSTANCE);
    public static final IElementType DIRECTIVE = new IElementType("DIRECTIVE", AnyDoorLanguage.INSTANCE);
    public static final IElementType DIRECTIVE_NAME = new IElementType("DIRECTIVE_NAME", AnyDoorLanguage.INSTANCE);
    public static final IElementType ASSIGNMENT = new IElementType("ASSIGNMENT", AnyDoorLanguage.INSTANCE);
    public static final IElementType DIRECTIVE_VALUE = new IElementType("DIRECTIVE_VALUE", AnyDoorLanguage.INSTANCE);
    public static final IElementType RUN = new IElementType("RUN", AnyDoorLanguage.INSTANCE);
    public static final IElementType CLASS_REFERENCE = new IElementType("CLASS_REFERENCE", AnyDoorLanguage.INSTANCE);
    public static final IElementType HASH = new IElementType("HASH", AnyDoorLanguage.INSTANCE);
    public static final IElementType METHOD_REFERENCE = new IElementType("METHOD_REFERENCE", AnyDoorLanguage.INSTANCE);
    public static final IElementType TYPE_REFERENCE = new IElementType("TYPE_REFERENCE", AnyDoorLanguage.INSTANCE);
    public static final IElementType TYPE_PARENTHESIS = new IElementType("TYPE_PARENTHESIS", AnyDoorLanguage.INSTANCE);
    public static final IElementType TYPE_SEPARATOR = new IElementType("TYPE_SEPARATOR", AnyDoorLanguage.INSTANCE);
    public static final IElementType VARIABLE = new IElementType("VARIABLE", AnyDoorLanguage.INSTANCE);
    public static final IElementType COMMENT = new IElementType("COMMENT", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON = new IElementType("JSON", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_KEY = new IElementType("JSON_KEY", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_STRING = new IElementType("JSON_STRING", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_NUMBER = new IElementType("JSON_NUMBER", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_BOOLEAN = new IElementType("JSON_BOOLEAN", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_NULL = new IElementType("JSON_NULL", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_PUNCTUATION = new IElementType("JSON_PUNCTUATION", AnyDoorLanguage.INSTANCE);
    public static final IElementType JSON_BODY = new IElementType("JSON_BODY", AnyDoorLanguage.INSTANCE);
    public static final IElementType WHITESPACE = com.intellij.psi.TokenType.WHITE_SPACE;
    private AnyDoorTokenTypes() { }
}
