package io.github.lgp547.anydoorplugin.request;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

/** Native PSI reference for the class segment of RUN class#method. */
final class AnyDoorClassReferenceElement extends ASTWrapperPsiElement {
    AnyDoorClassReferenceElement(@NotNull ASTNode node) { super(node); }
    @Override public PsiReference getReference() { return new ClassReference(this); }
    private static final class ClassReference extends PsiReferenceBase<PsiElement> {
        ClassReference(PsiElement element) { super(element, TextRange.allOf(element.getText()), true); }
        @Override public PsiElement resolve() {
            PsiClass target=JavaPsiFacade.getInstance(getElement().getProject()).findClass(getValue(), GlobalSearchScope.allScope(getElement().getProject()));
            if(target!=null&&target.isInterface()){PsiClass implementation=ClassInheritorsSearch.search(target).findFirst();if(implementation!=null)return implementation;}
            return target;
        }
        @Override public Object @NotNull [] getVariants() { return EMPTY_ARRAY; }
    }
}
