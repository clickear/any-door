package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Run icon beside every RUN line in an .anydoor file. */
public final class AnyDoorRequestLineMarkerProvider extends LineMarkerProviderDescriptor {
    @Override public @NotNull String getName() { return "Run AnyDoor request"; }
    @Override public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (element.getContainingFile().getVirtualFile() == null || !"anydoor".equalsIgnoreCase(element.getContainingFile().getVirtualFile().getExtension())) return null;
        if (!element.getText().trim().startsWith("RUN ")) return null;
        return new LineMarkerInfo<>(element, element.getTextRange(), AllIcons.Actions.Execute, pass -> "Run AnyDoor request", (event, ignored) -> {
            Project project = element.getProject();
            if (project != null) AnyDoorRequestRunner.runAt(project, element.getContainingFile().getText(), element.getTextOffset());
        }, com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT, () -> "Run AnyDoor request");
    }
}
