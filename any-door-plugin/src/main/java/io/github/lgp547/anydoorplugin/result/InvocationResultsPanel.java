package io.github.lgp547.anydoorplugin.result;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.List;

public final class InvocationResultsPanel extends JPanel implements Disposable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final InvocationResultService service;
    private final DefaultListModel<InvocationResult> historyModel = new DefaultListModel<>();
    private final JBList<InvocationResult> history = new JBList<>(historyModel);
    private final JTree tree = new JTree(new DefaultMutableTreeNode("No result selected"));
    private final JBTextArea raw = new JBTextArea();
    private final SearchTextField search = new SearchTextField(false);
    private final JLabel summary = new JLabel(" Select an invocation result");
    private AutoCloseable subscription;
    private String fullJson;

    public InvocationResultsPanel(Project project) {
        super(new BorderLayout());
        service = InvocationResultService.instance(project);
        build();
        subscription = service.listen(values -> ApplicationManager.getApplication().invokeLater(() -> refresh(values)));
    }

    private void build() {
        history.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        history.setCellRenderer((list, value, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(4, 2));
            row.setOpaque(true); row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            row.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            JLabel title = new JLabel(statusIcon(value.status()) + "  " + value.invocation());
            JLabel meta = new JLabel(value.target() + "  ·  " + TIME.format(Instant.ofEpochMilli(value.startedAt()))
                    + (value.status() == InvocationResult.Status.RUNNING ? "" : "  ·  " + value.durationMillis() + " ms"));
            meta.setForeground(UIManager.getColor("Label.disabledForeground"));
            row.add(title, BorderLayout.NORTH); row.add(meta, BorderLayout.SOUTH); return row;
        });
        history.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) show(history.getSelectedValue()); });

        JPanel toolbar = new JPanel(new BorderLayout(6, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton copyValue = new JButton("Copy Value"); copyValue.addActionListener(e -> copySelectedValue());
        JButton copyPath = new JButton("Copy Path"); copyPath.addActionListener(e -> copySelectedPath());
        JButton copyAll = new JButton("Copy JSON"); copyAll.addActionListener(e -> copy(fullJson));
        JButton expand = new JButton("Expand"); expand.addActionListener(e -> expandAll(true));
        JButton collapse = new JButton("Collapse"); collapse.addActionListener(e -> expandAll(false));
        JButton clear = new JButton("Clear"); clear.addActionListener(e -> service.clear());
        buttons.add(copyValue); buttons.add(copyPath); buttons.add(copyAll); buttons.add(expand); buttons.add(collapse); buttons.add(clear);
        toolbar.add(buttons, BorderLayout.WEST); toolbar.add(search, BorderLayout.CENTER);
        search.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { find(); } public void removeUpdate(DocumentEvent e) { find(); }
            public void changedUpdate(DocumentEvent e) { find(); }
        });

        raw.setEditable(false); raw.setFont(new Font(Font.MONOSPACED, Font.PLAIN, raw.getFont().getSize()));
        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab("Tree", new JBScrollPane(tree)); tabs.addTab("Raw", new JBScrollPane(raw));
        JPanel detail = new JPanel(new BorderLayout()); detail.add(summary, BorderLayout.NORTH); detail.add(tabs, BorderLayout.CENTER);
        JBSplitter splitter = new JBSplitter(false, 0.32f); splitter.setFirstComponent(new JBScrollPane(history)); splitter.setSecondComponent(detail);
        add(toolbar, BorderLayout.NORTH); add(splitter, BorderLayout.CENTER);
    }

    private void refresh(List<InvocationResult> values) {
        String selected = history.getSelectedValue() == null ? null : history.getSelectedValue().id();
        historyModel.clear(); values.forEach(historyModel::addElement);
        if (selected != null) for (int i = 0; i < historyModel.size(); i++) if (selected.equals(historyModel.get(i).id())) history.setSelectedIndex(i);
        if (history.getSelectedIndex() < 0 && !historyModel.isEmpty()) history.setSelectedIndex(0);
        if (historyModel.isEmpty()) show(null);
    }

    private void show(InvocationResult result) {
        if (result == null) { fullJson = null; raw.setText(""); summary.setText(" Select an invocation result"); tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No results"))); return; }
        summary.setText(" " + result.status() + "  ·  " + result.target() + "  ·  " + result.durationMillis() + " ms"
                + (result.error() == null ? "" : "  ·  " + result.error()));
        fullJson = result.status() == InvocationResult.Status.ERROR ? result.error() : result.json();
        raw.setText(fullJson == null ? "Waiting for the target JVM…" : fullJson); raw.setCaretPosition(0);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new JsonNode("$", "$", fullJson == null ? "running" : fullJson, fullJson == null ? "running" : abbreviate(fullJson)));
        if (result.status() == InvocationResult.Status.SUCCESS) {
            try { root = node("$", "$", JsonParser.parseString(result.json()), new int[]{10000}, 0); }
            catch (Exception ignored) { }
        }
        tree.setModel(new DefaultTreeModel(root)); tree.setRootVisible(true); expandFirstLevels();
    }

    private DefaultMutableTreeNode node(String name, String path, JsonElement element, int[] budget, int depth) {
        String rawValue = element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : element.toString();
        String display = element.isJsonObject() ? "{" + element.getAsJsonObject().size() + "}" : element.isJsonArray() ? "[" + element.getAsJsonArray().size() + "]" : abbreviate(rawValue);
        DefaultMutableTreeNode current = new DefaultMutableTreeNode(new JsonNode(name, path, rawValue, display));
        if (--budget[0] <= 0 || depth >= 100) { current.add(new DefaultMutableTreeNode("… tree truncated; use Raw view")); return current; }
        if (element.isJsonObject()) for (var entry : element.getAsJsonObject().entrySet()) {
            if (budget[0] <= 0) break;
            current.add(node(entry.getKey(), childPath(path, entry.getKey()), entry.getValue(), budget, depth + 1));
        }
        if (element.isJsonArray()) for (int i = 0; i < element.getAsJsonArray().size() && budget[0] > 0; i++) current.add(node("[" + i + "]", path + "[" + i + "]", element.getAsJsonArray().get(i), budget, depth + 1));
        return current;
    }

    private void find() {
        String query = search.getText().trim().toLowerCase(); if (query.isEmpty()) return;
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) { DefaultMutableTreeNode item = (DefaultMutableTreeNode) nodes.nextElement();
            Object value = item.getUserObject();
            String searchable = value instanceof JsonNode node ? node.name() + " " + node.path() + " " + node.rawValue() : item.toString();
            if (searchable.toLowerCase().contains(query)) { TreePath path = new TreePath(item.getPath()); tree.expandPath(path.getParentPath()); tree.setSelectionPath(path); tree.scrollPathToVisible(path); return; }
        }
    }

    private void copySelectedValue() { Object value = selected(); if (value instanceof JsonNode node) copy(node.rawValue()); }
    private void copySelectedPath() { Object value = selected(); if (value instanceof JsonNode node) copy(node.path()); }
    private Object selected() { return tree.getSelectionPath() == null ? null : ((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent()).getUserObject(); }
    private void copy(String value) { if (value != null) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null); }
    private void expandAll(boolean expand) { for (int i = tree.getRowCount() - 1; i >= 0; i--) if (expand) tree.expandRow(i); else tree.collapseRow(i); }
    private void expandFirstLevels() { tree.expandRow(0); for (int i = 1; i < Math.min(tree.getRowCount(), 20); i++) tree.expandRow(i); }
    private static String statusIcon(InvocationResult.Status status) { return switch (status) { case RUNNING -> "●"; case SUCCESS -> "✓"; case ERROR -> "✕"; }; }
    private static String abbreviate(String value) { return value.length() > 300 ? value.substring(0, 300) + "…" : value; }
    private static String childPath(String parent, String key) {
        return key.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? parent + "." + key : parent + "['" + key.replace("\\", "\\\\").replace("'", "\\'") + "']";
    }
    @Override public void dispose() { try { if (subscription != null) subscription.close(); } catch (Exception ignored) { } }
    private record JsonNode(String name, String path, String rawValue, String display) { @Override public String toString() { return "$".equals(name) ? name + "  " + display : name + ": " + display; } }
}
