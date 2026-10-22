package io.github.lgp547.anydoorplugin.dialog.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.dialog.MethodDataContext;
import io.github.lgp547.anydoorplugin.util.JsonUtil;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** A preview-first chooser. Selecting a row never mutates the editor. */
final class ParamPresetChooser {
    private ParamPresetChooser() { }

    static void show(Project project, Component anchor, MethodDataContext context, MyEditor editor) {
        SearchTextField search = new SearchTextField();
        DefaultListModel<ParamDataItem> model = new DefaultListModel<>();
        JBList<ParamDataItem> list = new JBList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((source, value, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(8, 2));
            row.setOpaque(true);
            row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            row.setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            String name = value == null || value.getName() == null || value.getName().isBlank() ? "未命名参数方案" : value.getName();
            String star = value != null && Boolean.TRUE.equals(value.getFavorite()) ? "★ " : "";
            JBLabel title = new JBLabel(star + name);
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            JBLabel meta = new JBLabel(summary(value));
            meta.setForeground(selected ? source.getSelectionForeground() : com.intellij.ui.JBColor.GRAY);
            row.add(title, BorderLayout.NORTH); row.add(meta, BorderLayout.SOUTH);
            return row;
        });

        JBTextArea diff = new JBTextArea("选择方案以预览差异");
        diff.setEditable(false); diff.setLineWrap(false);
        diff.setFont(new Font(Font.MONOSPACED, Font.PLAIN, diff.getFont().getSize()));
        JBLabel previewTitle = new JBLabel("与当前草稿的差异（尚未应用）");
        JPanel preview = new JPanel(new BorderLayout());
        preview.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        preview.add(previewTitle, BorderLayout.NORTH); preview.add(new JBScrollPane(diff), BorderLayout.CENTER);

        List<ParamDataItem> presets = context.listPersistedData();
        Runnable refill = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            List<ParamDataItem> filtered = presets.stream()
                    .filter(item -> query.isEmpty() || searchable(item).contains(query))
                    .sorted(Comparator.comparing((ParamDataItem item) -> !Boolean.TRUE.equals(item.getFavorite()))
                            .thenComparing(ParamDataItem::getLastUsedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(ParamDataItem::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .collect(Collectors.toList());
            model.clear(); filtered.forEach(model::addElement);
            if (!filtered.isEmpty()) list.setSelectedIndex(0);
        };
        search.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override protected void textChanged(@org.jetbrains.annotations.NotNull javax.swing.event.DocumentEvent event) { refill.run(); }
        });
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) diff.setText(renderDiff(editor.getText(), list.getSelectedValue()));
        });
        refill.run();

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(search, BorderLayout.NORTH); left.add(new JBScrollPane(list), BorderLayout.CENTER);
        JPanel body = new JPanel(new BorderLayout(8, 0));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(left, BorderLayout.CENTER); preview.setPreferredSize(new Dimension(300, 320)); body.add(preview, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton merge = new JButton("仅合并缺失字段");
        JButton apply = new JButton("应用全部");
        actions.add(merge); actions.add(apply);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(new JBLabel("选择参数方案 · 选择只预览，点击应用才会改动本次调用"), BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER); root.add(actions, BorderLayout.SOUTH);

        JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(root, search)
                .setTitle("参数方案")
                .setResizable(true)
                .setMovable(true)
                .setRequestFocus(true)
                .createPopup();
        apply.addActionListener(event -> {
            ParamDataItem selected = list.getSelectedValue();
            if (selected != null && context.applyPreset(selected.getId())) popup.closeOk(null);
        });
        merge.addActionListener(event -> {
            ParamDataItem selected = list.getSelectedValue();
            if (selected != null) editor.setText(mergeMissing(editor.getText(), selected.getParam()));
            popup.closeOk(null);
        });
        popup.showUnderneathOf(anchor);
    }

    private static String searchable(ParamDataItem item) {
        return (Objects.toString(item.getName(), "") + " " + Objects.toString(item.getParam(), "")).toLowerCase(Locale.ROOT);
    }

    private static String summary(ParamDataItem item) {
        if (item == null || item.getParam() == null) return "";
        String compact = item.getParam().replaceAll("\\s+", " ");
        return compact.length() > 72 ? compact.substring(0, 72) + "…" : compact;
    }

    private static String renderDiff(String current, ParamDataItem preset) {
        if (preset == null) return "选择方案以预览差异";
        try {
            JsonNode before = JsonUtil.objectMapper.readTree(current);
            JsonNode after = JsonUtil.objectMapper.readTree(preset.getParam());
            List<String> lines = new ArrayList<>();
            diff("", before, after, lines);
            return lines.isEmpty() ? "与当前草稿相同" : String.join("\n", lines);
        } catch (Exception ignored) {
            return "当前草稿不是合法 JSON；应用前请先修正。";
        }
    }

    private static void diff(String path, JsonNode before, JsonNode after, List<String> lines) {
        if (before != null && before.isObject() && after != null && after.isObject()) {
            java.util.Iterator<String> fields = before.fieldNames();
            while (fields.hasNext()) { String field = fields.next(); diff(path(path, field), before.get(field), after.get(field), lines); }
            java.util.Iterator<String> added = after.fieldNames();
            while (added.hasNext()) { String field = added.next(); if (!before.has(field)) lines.add("+ " + path(path, field) + ": " + after.get(field)); }
            return;
        }
        if (before == null && after != null) lines.add("+ " + path + ": " + after);
        else if (before != null && after == null) lines.add("- " + path + ": " + before);
        else if (!Objects.equals(before, after)) lines.add("~ " + path + ": " + before + " → " + after);
    }

    private static String path(String parent, String field) { return parent.isEmpty() ? field : parent + "." + field; }

    private static String mergeMissing(String current, String preset) {
        try {
            JsonNode currentNode = JsonUtil.objectMapper.readTree(current);
            JsonNode presetNode = JsonUtil.objectMapper.readTree(preset);
            if (currentNode instanceof ObjectNode && presetNode instanceof ObjectNode) merge((ObjectNode) currentNode, (ObjectNode) presetNode);
            return JsonUtil.formatterJson(currentNode.toString());
        } catch (Exception ignored) { return current; }
    }

    private static void merge(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(entry -> {
            JsonNode current = target.get(entry.getKey());
            if (current == null || current.isNull()) target.set(entry.getKey(), entry.getValue());
            else if (current instanceof ObjectNode && entry.getValue() instanceof ObjectNode) merge((ObjectNode) current, (ObjectNode) entry.getValue());
        });
    }
}
