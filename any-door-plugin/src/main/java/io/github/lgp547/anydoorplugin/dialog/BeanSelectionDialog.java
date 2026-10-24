package io.github.lgp547.anydoorplugin.dialog;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** A selection is confirmed for each ambiguous invocation; cancelling never executes it. */
public final class BeanSelectionDialog extends DialogWrapper {
    private final JList<String> beans;
    private final List<JsonObject> candidates;

    public BeanSelectionDialog(Project project, String className, List<JsonObject> candidates) {
        super(project, true);
        this.candidates = candidates;
        setTitle("选择 Spring Bean — " + className);
        setOKButtonText("使用所选 Bean 调用");
        beans = new JList<>(candidates.stream().map(BeanSelectionDialog::label).toArray(String[]::new));
        beans.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        beans.setSelectedIndex(0);
        init();
    }

    private static String label(JsonObject bean) {
        return bean.get("beanName").getAsString() + "   |   " + bean.get("proxyType").getAsString()
                + "   |   " + bean.get("contextName").getAsString() + " [" + bean.get("beanContextId").getAsString() + "]"
                + (bean.has("classLoaderId") ? "   |   loader " + bean.get("classLoaderId").getAsString() : "");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("发现多个实例，请选择本次调用的 Bean（名称 / 代理类型 / 所属容器）"), BorderLayout.NORTH);
        JBScrollPane scroll = new JBScrollPane(beans);
        scroll.setPreferredSize(new JBDimension(650, 180));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    public JsonObject getSelectedBean() {
        return candidates.get(beans.getSelectedIndex());
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return beans.getSelectedIndex() < 0 ? new ValidationInfo("请选择一个 Bean", beans) : null;
    }
}
