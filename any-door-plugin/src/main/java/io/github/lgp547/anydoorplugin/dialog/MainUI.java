package io.github.lgp547.anydoorplugin.dialog;

import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.*;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import io.github.lgp547.anydoorplugin.dialog.components.MainPanel;
import io.github.lgp547.anydoorplugin.dialog.event.Event;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.event.DefaultMulticaster;
import io.github.lgp547.anydoorplugin.dialog.event.Listener;
import io.github.lgp547.anydoorplugin.dialog.event.impl.DataSyncEvent;
import io.github.lgp547.anydoorplugin.dialog.utils.EventHelper;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import io.github.lgp547.anydoorplugin.util.ParamValidationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @description:
 * @author: zhouh
 * @date: 2023-07-07 11:35
 **/
public class MainUI extends DialogWrapper implements Listener {

    private final Project project;

    private final JPanel rootPanel;

    private MethodDataContext context;

    private MainPanel panel;

    private Consumer<String> okAction;

    public MainUI(String title, Project project) {
        super(project, true, IdeModalityType.MODELESS);
        setTitle(title);
        this.project = project;
        this.rootPanel = new JPanel(new BorderLayout());

        buttonSettings();
        DefaultMulticaster.getInstance(project).addListener(this);

        rootPanel.add(new JLabel("Loading..."), BorderLayout.CENTER);
        init();
    }

    public void bindContext(MethodDataContext context) {
        this.context = context;
        this.panel = new MainPanel(project, context);
        rootPanel.removeAll();
        rootPanel.add(panel, BorderLayout.CENTER);
        rootPanel.revalidate();
        rootPanel.repaint();
    }

    private void buttonSettings() {
        setOKButtonText("RUN");
        setOKButtonIcon(AllIcons.Actions.RunAll);
    }

    public void setOkAction(Consumer<String> runnable) {
        this.okAction = runnable;
    }

    @Override
    protected void doOKAction() {
        if (doValidate() != null) {
            return;
        }
        if (Objects.nonNull(okAction) && panel != null) {
            JSONEditor editor = panel.getEditor();
            String text = JsonUtil.compressJson(editor.getText());
            okAction.accept(text);
        }
        super.doOKAction();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (panel == null || context == null) {
            return null;
        }
        AnyDoorSettingsState settings = project.getService(AnyDoorSettingsState.class);
        ValidationInfo validationInfo = ParamValidationUtil.validate(panel.getEditor().getText(), context.getParamList(),
                settings == null ? null : settings.jsonDateTimeFormat, panel.getEditor());
        if (validationInfo != null) {
            return validationInfo;
        }
        return super.doValidate();
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return rootPanel;
    }

    @Override
    public void onEvent(Event event) {
        if (Objects.equals(EventType.DATA_SYNC, event.getType()) && context != null) {
            DataSyncEvent syncEvent = (DataSyncEvent) event;
            if (Objects.equals(syncEvent.getQualifiedMethodName(), context.getQualifiedMethodName()) && context.getClazz() != null) {
                DataContext.instance(project).getExecuteDataContextAsync(
                        context.getClazz().getQualifiedName(),
                        context.getQualifiedMethodName(),
                        context.getSelectedItem() == null ? null : context.getSelectedItem().getId(),
                        context.cacheContent,
                        latest -> {
                            bindContext(latest);
                            latest.fireEvent(EventHelper.createDisplayDataChangeEvent(latest.listDisplayData(), latest.getSelectedItem()));
                        }
                );
            }
        }
    }

    @Override
    protected void dispose() {
        DefaultMulticaster.getInstance(project).removeListener(this);
        super.dispose();
    }

    public Integer getRunNum() {
        return panel != null ? panel.getRunNum() : 1;
    }

    public Boolean getIsConcurrent() {
        return panel != null ? panel.getIsConcurrent() : false;
    }

    public boolean isChangePid() {
        return panel != null && panel.isChangePid();
    }

    public Integer getPid() {
        return panel != null ? panel.getPid() : null;
    }
}
