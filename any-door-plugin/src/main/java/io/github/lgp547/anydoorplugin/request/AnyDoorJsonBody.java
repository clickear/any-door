package io.github.lgp547.anydoorplugin.request;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/** A dedicated JSON host lets the bundled JSON language provide validation and editor behaviour. */
final class AnyDoorJsonBody extends ASTWrapperPsiElement implements PsiLanguageInjectionHost {
    AnyDoorJsonBody(@NotNull ASTNode node) { super(node); }
    @Override public boolean isValidHost() { return true; }
    @Override public @NotNull PsiLanguageInjectionHost updateText(@NotNull String text) {
        int offset=getTextRange().getStartOffset();
        Document document=PsiDocumentManager.getInstance(getProject()).getDocument(getContainingFile());
        if(document==null)return this;
        document.replaceString(offset, getTextRange().getEndOffset(), text);
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        var leaf=getContainingFile().findElementAt(offset);
        AnyDoorJsonBody replacement=leaf==null?null:PsiTreeUtil.getParentOfType(leaf, AnyDoorJsonBody.class, false);
        return replacement==null?this:replacement;
    }
    @Override public @NotNull LiteralTextEscaper<AnyDoorJsonBody> createLiteralTextEscaper() {
        return new LiteralTextEscaper<>(this) {
            @Override public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) { outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset()); return true; }
            @Override public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) { return rangeInsideHost.getStartOffset()+offsetInDecoded; }
            @Override public boolean isOneLine() { return false; }
        };
    }
}
