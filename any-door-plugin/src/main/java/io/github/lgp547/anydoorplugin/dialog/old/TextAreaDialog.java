package io.github.lgp547.anydoorplugin.dialog.old;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiParameterList;
import io.github.lgp547.anydoorplugin.dialog.JSONEditor;
import io.github.lgp547.anydoorplugin.dto.ParamCacheDto;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.ParamValidationUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TextAreaDialog extends DialogWrapper {

    private final JSONEditor textArea;
    private final ContentPanel contentPanel;
    private final AnyDoorSettingsState service;
    private final PsiParameterList psiParameterList;

    private Runnable okAction;

    public TextAreaDialog(Project project, String title, PsiParameterList psiParameterList, ParamCacheDto paramCacheDto, AnyDoorSettingsState service) {
        super(project, true, IdeModalityType.MODELESS);
        setTitle(title);
        this.service = service;
        this.psiParameterList = psiParameterList;
        textArea = new JSONEditor(paramCacheDto.content(), psiParameterList, project);
        contentPanel = new ContentPanel(textArea);
        contentPanel.addCacheButtonListener(e -> textArea.genCacheContent());
        contentPanel.addSimpleButtonListener(e -> textArea.genSimpleContent());
        contentPanel.addJsonButtonListener(e -> textArea.genJsonContent());
        contentPanel.addJsonToQueryButtonListener(e -> textArea.jsonToQuery());
        contentPanel.addQueryToJsonButtonListener(e -> textArea.queryToJson());
        contentPanel.initRunNum(paramCacheDto.getRunNum());
        contentPanel.initIsConcurrent(paramCacheDto.getConcurrent());
        contentPanel.initPid(service.pid);

        init();
    }

    public void setOkAction(Runnable runnable) {
        okAction = runnable;
    }

    public String getText() {
        return textArea.getText();
    }

    public Integer getRunNum() {
        return contentPanel.getRunNum();
    }

    public Boolean getIsConcurrent() {
        return contentPanel.getIsConcurrent();
    }

    public Long getPid() {
        return contentPanel.getPid();
    }

    @Override
    protected void doOKAction() {
        if (doValidate() != null) {
            return;
        }
        okAction.run();
        super.doOKAction();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        ValidationInfo validationInfo = ParamValidationUtil.validate(textArea.getText(), psiParameterList, service.jsonDateTimeFormat, textArea);
        if (validationInfo != null) {
            return validationInfo;
        }
        return super.doValidate();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return contentPanel;
    }

    public boolean isChangePid() {
        return contentPanel.isChangePid();
    }
}
