package io.github.lgp547.anydoorplugin.dialog.components;

/**
 * @description:
 * @author: zhouh
 * @date: 2023-07-18 11:14
 **/

import java.util.Objects;
import java.util.Optional;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParameter;
import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.dialog.JSONEditor;
import io.github.lgp547.anydoorplugin.dialog.MethodDataContext;
import io.github.lgp547.anydoorplugin.dialog.event.Event;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.event.Listener;
import io.github.lgp547.anydoorplugin.dialog.event.impl.DisplayDataChangeEvent;
import io.github.lgp547.anydoorplugin.dialog.expression.FieldSelectionResolver;
import io.github.lgp547.anydoorplugin.dialog.expression.FieldTarget;
import io.github.lgp547.anydoorplugin.dialog.utils.EventHelper;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class MyEditor extends JSONEditor implements Listener {

    private final MethodDataContext context;
    private final FieldSelectionResolver fieldSelectionResolver = new FieldSelectionResolver();

    public MyEditor(MethodDataContext context, String cacheText, @Nullable PsiParameterList psiParameterList, Project project) {
        super(cacheText, psiParameterList, project);
        this.context = context;

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                publishCurrentFieldTarget();
            }
        });

//        addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                String text = getText();
//                context.updateCache(text);
//            }
//        });
    }

    @Override
    public void onEvent(Event event) {
        if (Objects.equals(event.getType(), EventType.DISPLAY_DATA_CHANGE)) {
            ParamDataItem selectedItem = ((DisplayDataChangeEvent) event).getSelectedItem();
            if (selectedItem != null) {
                String text = selectedItem.getParam();
                setText(JsonUtil.formatterJson(text));
                publishCurrentFieldTarget();
            }
        }
    }

    private void publishCurrentFieldTarget() {
        Editor editor = getEditor();
        if (editor == null) {
            return;
        }
        CaretModel caretModel = editor.getCaretModel();
        int caretOffset = caretModel.getOffset();
        Optional<FieldTarget> targetOpt = resolveCurrentFieldTarget(caretOffset);
        context.fireEvent(EventHelper.createFieldTargetChangedEvent(targetOpt.orElse(null)));
    }

    private Optional<FieldTarget> resolveCurrentFieldTarget(int caretOffset) {
        return fieldSelectionResolver.resolveFromJsonCaret("root", "java.lang.Object", getText(), caretOffset)
                .flatMap(this::remapTargetToMethodParameter);
    }

    private Optional<FieldTarget> remapTargetToMethodParameter(FieldTarget rawTarget) {
        String[] pathParts = rawTarget.jsonPath().split("\\.");
        if (pathParts.length == 0 || context.getParamList().getParametersCount() == 0) {
            return Optional.empty();
        }
        for (int i = 0; i < context.getParamList().getParametersCount(); i++) {
            PsiParameter parameter = context.getParamList().getParameter(i);
            if (parameter != null && Objects.equals(parameter.getName(), pathParts[0])) {
                return Optional.of(new FieldTarget(
                        parameter.getName(),
                        rawTarget.jsonPath(),
                        rawTarget.displayPath(),
                        parameter.getType().getCanonicalText(),
                        rawTarget.currentJsonValue(),
                        rawTarget.source()
                ));
            }
        }
        if (context.getParamList().getParametersCount() == 1) {
            PsiParameter parameter = context.getParamList().getParameter(0);
            if (parameter != null) {
                String jsonPath = parameter.getName() + "." + rawTarget.jsonPath();
                return Optional.of(new FieldTarget(
                        parameter.getName(),
                        jsonPath,
                        jsonPath,
                        parameter.getType().getCanonicalText(),
                        rawTarget.currentJsonValue(),
                        rawTarget.source()
                ));
            }
        }
        return Optional.empty();
    }
}
