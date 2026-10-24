package io.github.lgp547.anydoorplugin.request;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiParser;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/** Minimal PSI tree: leaf tokens are sufficient for references and language services. */
public final class AnyDoorParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(AnyDoorLanguage.INSTANCE);
    @Override public @NotNull Lexer createLexer(Project project) { return new AnyDoorLexer(); }
    @Override public @NotNull PsiParser createParser(Project project) { return (root, builder) -> { var mark=builder.mark(); while(!builder.eof()) { if (builder.getTokenType()==AnyDoorTokenTypes.JSON_PUNCTUATION && "{".equals(builder.getTokenText())) parseJsonBody(builder); else builder.advanceLexer(); } mark.done(root); return builder.getTreeBuilt(); }; }
    @Override public @NotNull IFileElementType getFileNodeType() { return FILE; }
    @Override public @NotNull TokenSet getWhitespaceTokens() { return TokenSet.create(TokenType.WHITE_SPACE); }
    @Override public @NotNull TokenSet getCommentTokens() { return TokenSet.create(AnyDoorTokenTypes.COMMENT); }
    @Override public @NotNull TokenSet getStringLiteralElements() { return TokenSet.EMPTY; }
    @Override public @NotNull PsiElement createElement(ASTNode node) { if(node.getElementType()==AnyDoorTokenTypes.JSON_BODY)return new AnyDoorJsonBody(node);if(node.getElementType()==AnyDoorTokenTypes.CLASS_REFERENCE)return new AnyDoorClassReferenceElement(node);return new com.intellij.extapi.psi.ASTWrapperPsiElement(node); }
    @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider provider) { return new PsiFileBase(provider, AnyDoorLanguage.INSTANCE) { @Override public @NotNull com.intellij.openapi.fileTypes.FileType getFileType() { return AnyDoorRequestFileType.INSTANCE; } }; }
    private static void parseJsonBody(PsiBuilder builder) { PsiBuilder.Marker json=builder.mark(); int depth=0; do { if(builder.getTokenType()==AnyDoorTokenTypes.JSON_PUNCTUATION){String token=builder.getTokenText();if("{".equals(token))depth++;if("}".equals(token))depth--;} builder.advanceLexer(); } while(!builder.eof()&&depth>0); json.done(AnyDoorTokenTypes.JSON_BODY); }
}
