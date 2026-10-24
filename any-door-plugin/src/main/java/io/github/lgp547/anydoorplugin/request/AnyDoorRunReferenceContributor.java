package io.github.lgp547.anydoorplugin.request;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/** Supplies references from RUN class segments even though the request file intentionally has a minimal PSI tree. */
public final class AnyDoorRunReferenceContributor extends PsiReferenceContributor {
    @Override public void registerReferenceProviders(@NotNull com.intellij.psi.PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new PsiReferenceProvider() {
            @Override public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                if (element.getContainingFile().getVirtualFile() == null || !"anydoor".equalsIgnoreCase(element.getContainingFile().getVirtualFile().getExtension())) return PsiReference.EMPTY_ARRAY;
                if (element.getNode().getElementType() != AnyDoorTokenTypes.CLASS_REFERENCE) return PsiReference.EMPTY_ARRAY;
                return new PsiReference[]{new RunClassReference(element, TextRange.allOf(element.getText()))};
            }
        });
    }
    private static final class RunClassReference extends PsiReferenceBase<PsiElement> {
        private RunClassReference(PsiElement element, TextRange range) { super(element, range, true); }
        @Override public PsiElement resolve() {
            PsiClass target = JavaPsiFacade.getInstance(getElement().getProject()).findClass(getValue(), GlobalSearchScope.allScope(getElement().getProject()));
            if (target == null) return null;
            if (target.isInterface()) { PsiClass implementation = ClassInheritorsSearch.search(target).findFirst(); if (implementation != null) return implementation; }
            return target;
        }
        @Override public Object @NotNull [] getVariants() { return new Object[0]; }
    }
}
