package io.github.lgp547.anydoorplugin.dialog.expression;

import com.intellij.openapi.project.Project;
import io.github.lgp547.anydoorplugin.dialog.MethodDataContext;

public interface ExpressionExecutionService {
    ExpressionResult execute(Project project, MethodDataContext context, ExpressionRequest request);
}
