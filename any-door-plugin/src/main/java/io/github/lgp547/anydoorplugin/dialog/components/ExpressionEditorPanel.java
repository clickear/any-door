package io.github.lgp547.anydoorplugin.dialog.components;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import io.github.lgp547.anydoorplugin.dialog.MethodDataContext;
import io.github.lgp547.anydoorplugin.dialog.event.Event;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.event.Listener;
import io.github.lgp547.anydoorplugin.dialog.event.impl.FieldTargetChangedEvent;
import io.github.lgp547.anydoorplugin.dialog.expression.DefaultExpressionExecutionService;
import io.github.lgp547.anydoorplugin.dialog.expression.ExpressionExecutionService;
import io.github.lgp547.anydoorplugin.dialog.expression.ExpressionRequest;
import io.github.lgp547.anydoorplugin.dialog.expression.ExpressionResult;
import io.github.lgp547.anydoorplugin.dialog.expression.FieldExpressionCache;
import io.github.lgp547.anydoorplugin.dialog.expression.FieldExpressionCacheKey;
import io.github.lgp547.anydoorplugin.dialog.expression.FieldTarget;
import io.github.lgp547.anydoorplugin.dialog.expression.JsonFieldPatchService;
import io.github.lgp547.anydoorplugin.dialog.components.intellij.MyXDebuggerExpressionEditor;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import io.github.lgp547.anydoorplugin.util.NotifierUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import java.awt.*;

public class ExpressionEditorPanel extends JBPanel<ExpressionEditorPanel> implements Listener {
    private final Project project;
    private final MethodDataContext context;
    private final MyEditor jsonEditor;
    private final JLabel targetLabel = new JLabel("Current field: unavailable");
    private final JButton applyButton = new JButton("Apply To Current Field");
    private final JButton clearButton = new JButton("Clear");
    private final MyXDebuggerExpressionEditor expressionEditor;
    private final FieldExpressionCache cache = new FieldExpressionCache();
    private final JsonFieldPatchService patchService = new JsonFieldPatchService();
    private final ExpressionExecutionService executionService = new DefaultExpressionExecutionService();

    private FieldTarget currentTarget;

    public ExpressionEditorPanel(Project project, MethodDataContext context, MyEditor jsonEditor) {
        super(new BorderLayout(0, 8));
        this.project = project;
        this.context = context;
        this.jsonEditor = jsonEditor;
        this.expressionEditor = new MyXDebuggerExpressionEditor(
                project,
                new JavaDebuggerEditorsProvider(),
                "anyDoorExpressionFieldFill",
                null,
                com.intellij.xdebugger.impl.breakpoints.XExpressionImpl.EMPTY_EXPRESSION,
                true,
                true,
                false
        );

        JBPanel<?> top = new JBPanel<>(new BorderLayout(8, 0));
        top.add(targetLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(clearButton);
        buttons.add(applyButton);
        top.add(buttons, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(expressionEditor.getComponent(), BorderLayout.CENTER);

        clearButton.addActionListener(e -> expressionEditor.setExpression(com.intellij.xdebugger.impl.breakpoints.XExpressionImpl.EMPTY_EXPRESSION));
        applyButton.addActionListener(e -> applyCurrentExpression());
        setCurrentTarget(null);
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == EventType.FIELD_TARGET_CHANGED) {
            setCurrentTarget(((FieldTargetChangedEvent) event).getTarget());
        }
    }

    private void setCurrentTarget(FieldTarget target) {
        this.currentTarget = target;
        if (target == null) {
            targetLabel.setText("Current field: unavailable");
            applyButton.setEnabled(false);
            return;
        }
        targetLabel.setText("Current field: " + target.displayPath() + " (" + target.declaredType() + ")");
        applyButton.setEnabled(true);
        cache.get(new FieldExpressionCacheKey(context.getMethodKey(), target.jsonPath()))
                .ifPresent(text -> expressionEditor.setExpression(new com.intellij.xdebugger.impl.breakpoints.XExpressionImpl(text, null, null, com.intellij.xdebugger.evaluation.EvaluationMode.CODE_FRAGMENT)));
    }

    private void applyCurrentExpression() {
        if (currentTarget == null) {
            return;
        }
        String expression = expressionEditor.getDocument().getText().trim();
        if (expression.isEmpty()) {
            NotifierUtil.notifyError(project, "Java expression is empty");
            return;
        }
        ExpressionRequest request = new ExpressionRequest(
                context.getMethodKey(),
                currentTarget,
                expression,
                jsonEditor.getText()
        );
        Window window = SwingUtilities.getWindowAncestor(this);
        Cursor oldCursor = window == null ? null : window.getCursor();
        try {
            if (window != null) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
            ExpressionResult result = executionService.execute(project, context, request);
            String patched = patchService.patch(jsonEditor.getText(), currentTarget.jsonPath(), result.valueJson());
            jsonEditor.setText(JsonUtil.formatterJson(patched));
            cache.put(new FieldExpressionCacheKey(context.getMethodKey(), currentTarget.jsonPath()), expression);
        } catch (Exception e) {
            NotifierUtil.notifyError(project, "Apply java expression failed: " + e.getMessage());
        } finally {
            if (window != null) {
                window.setCursor(oldCursor == null ? Cursor.getDefaultCursor() : oldCursor);
            }
        }
    }
}
