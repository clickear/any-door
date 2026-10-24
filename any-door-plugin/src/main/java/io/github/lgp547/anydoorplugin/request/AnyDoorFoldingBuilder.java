package io.github.lgp547.anydoorplugin.request;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Collapses a long request payload without hiding the request target or title. */
public final class AnyDoorFoldingBuilder implements FoldingBuilder {
    @Override public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull ASTNode root, @NotNull Document document) {
        Collection<AnyDoorJsonBody> bodies = PsiTreeUtil.findChildrenOfType(root.getPsi(), AnyDoorJsonBody.class);
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        for (AnyDoorJsonBody body : bodies) {
            TextRange range = body.getTextRange();
            if (document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset())) {
                descriptors.add(new FoldingDescriptor(body.getNode(), range));
            }
        }
        return descriptors.toArray(FoldingDescriptor.EMPTY);
    }
    @Override public String getPlaceholderText(@NotNull ASTNode node) { return "{ … }"; }
    @Override public boolean isCollapsedByDefault(@NotNull ASTNode node) { return false; }
}
