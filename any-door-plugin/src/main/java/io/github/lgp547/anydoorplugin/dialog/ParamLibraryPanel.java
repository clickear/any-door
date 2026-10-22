package io.github.lgp547.anydoorplugin.dialog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import io.github.lgp547.anydoorplugin.action.AnyDoorAgainOpenAction;
import io.github.lgp547.anydoorplugin.action.AnyDoorPerformed;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import io.github.lgp547.anydoorplugin.dialog.components.CustomToolbar;
import io.github.lgp547.anydoorplugin.dialog.event.DefaultMulticaster;
import io.github.lgp547.anydoorplugin.dialog.event.Event;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.event.Listener;
import io.github.lgp547.anydoorplugin.dialog.library.ParamLibraryTreeModel;
import io.github.lgp547.anydoorplugin.dialog.library.ParamMutationResult;
import io.github.lgp547.anydoorplugin.dialog.perf.LatestRequestToken;
import io.github.lgp547.anydoorplugin.dialog.utils.IdeClassUtil;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.AnyDoorActionUtil;
import io.github.lgp547.anydoorplugin.util.AnyDoorIcons;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import io.github.lgp547.anydoorplugin.util.NotifierUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Objects;

public class ParamLibraryPanel extends JPanel implements Listener, Disposable {

    private static final int FILTER_DEBOUNCE_MS = 150;

    private final Project project;
    private final SearchTextField searchField;
    private final Tree tree;
    private final JBTextArea preview;
    private final JBLabel statusLabel;
    private final Alarm filterAlarm;
    private final LatestRequestToken previewToken = new LatestRequestToken();

    private List<ParamIndexData> indexItems = List.of();
    private String activeClassName;
    private boolean initialClassLocated;

    public ParamLibraryPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.filterAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        this.searchField = new SearchTextField();
        this.tree = new Tree();
        this.preview = new JBTextArea();
        this.statusLabel = new JBLabel("正在加载参数库...");

        configureTree();
        configurePreview();
        buildLayout();
        registerListeners();

        DefaultMulticaster.getInstance(project).addListener(this);
        activeClassName = selectedClassName();
        reloadIndex();
    }

    private void configureTree() {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setEditable(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        LibraryTreeRenderer renderer = new LibraryTreeRenderer();
        tree.setCellRenderer(renderer);
        tree.setCellEditor(new PresetTreeCellEditor(tree, renderer));
        ToolTipManager.sharedInstance().registerComponent(tree);
    }

    private void configurePreview() {
        preview.setEditable(false);
        preview.setLineWrap(false);
        preview.setText("选择一个参数方案以预览 JSON");
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, preview.getFont().getSize()));
    }

    private void buildLayout() {
        JPanel top = new JPanel(new BorderLayout());
        top.add(createToolbar(), BorderLayout.NORTH);
        top.add(searchField, BorderLayout.SOUTH);

        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.add(top, BorderLayout.NORTH);
        treePanel.add(new JBScrollPane(tree), BorderLayout.CENTER);
        treePanel.add(statusLabel, BorderLayout.SOUTH);

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(new JBLabel("参数预览（只读）"), BorderLayout.NORTH);
        previewPanel.add(new JBScrollPane(preview), BorderLayout.CENTER);

        JSplitPane splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treePanel, previewPanel);
        splitter.setResizeWeight(0.65);
        splitter.setBorder(null);
        add(splitter, BorderLayout.CENTER);
    }

    private JComponent createToolbar() {
        CustomToolbar toolbar = new CustomToolbar();
        toolbar.addToolButton("Refresh", AnyDoorIcons.refresh_icon, AnyDoorIcons.refresh_hover_icon, e -> reloadIndex());
        toolbar.addToolButton("Open", AnyDoorIcons.edit_icon, AnyDoorIcons.edit_hover_icon, e -> openSelectedPreset());
        toolbar.addToolButton("Rename", AnyDoorIcons.edit_icon, AnyDoorIcons.edit_hover_icon, e -> startRename());
        toolbar.addToolButton("Delete", AnyDoorIcons.delete_icon, AnyDoorIcons.delete_hover_icon, e -> deleteSelectedPresets());
        toolbar.addToolButton("Copy JSON", e -> copyPreview());
        return toolbar;
    }

    private void registerListeners() {
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                filterAlarm.cancelAllRequests();
                filterAlarm.addRequest(ParamLibraryPanel.this::rebuildTree, FILTER_DEBOUNCE_MS);
            }
        });
        tree.addTreeSelectionListener(this::selectionChanged);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    openSelectedPreset();
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                showPopupIfNecessary(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopupIfNecessary(event);
            }
        });
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_F2) {
                    startRename();
                } else if (event.getKeyCode() == KeyEvent.VK_DELETE || event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedPresets();
                } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelectedPreset();
                }
            }
        });

        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                activeClassName = classNameOf(event.getNewFile());
                tree.repaint();
            }
        });
    }

    private void showPopupIfNecessary(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path != null && !tree.isPathSelected(path)) {
            tree.setSelectionPath(path);
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("打开调用窗口");
        open.addActionListener(e -> openSelectedPreset());
        open.setEnabled(singleSelectedPreset() != null);
        menu.add(open);

        JMenuItem rename = new JMenuItem("重命名");
        rename.addActionListener(e -> startRename());
        rename.setEnabled(singleSelectedPreset() != null);
        menu.add(rename);

        JMenuItem copy = new JMenuItem("复制 JSON");
        copy.addActionListener(e -> copyPreview());
        copy.setEnabled(singleSelectedPreset() != null);
        menu.add(copy);

        JMenuItem delete = new JMenuItem("删除");
        delete.addActionListener(e -> deleteSelectedPresets());
        delete.setEnabled(!selectedPresetNodes().isEmpty());
        menu.add(delete);
        menu.show(tree, event.getX(), event.getY());
    }

    private void reloadIndex() {
        statusLabel.setText("正在加载参数库...");
        DataContext.instance(project).listParamIndexDataAsync(items -> {
            if (Disposer.isDisposed(this)) {
                return;
            }
            indexItems = items;
            rebuildTree();
        });
    }

    private void rebuildTree() {
        ParamLibraryTreeModel.Node root = ParamLibraryTreeModel.build(indexItems, searchField.getText());
        LibraryTreeModel model = new LibraryTreeModel(toSwingNode(root));
        tree.setModel(model);
        statusLabel.setText(root.getChildren().isEmpty()
                ? (searchField.getText().isBlank() ? "暂无参数方案" : "没有匹配的参数方案")
                : indexItems.size() + " 个参数方案");

        if (!searchField.getText().isBlank()) {
            expandAll();
        } else if (!initialClassLocated && activeClassName != null) {
            locateClass(activeClassName);
            initialClassLocated = true;
        }
    }

    private DefaultMutableTreeNode toSwingNode(ParamLibraryTreeModel.Node node) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(node);
        node.getChildren().forEach(child -> swingNode.add(toSwingNode(child)));
        return swingNode;
    }

    private void expandAll() {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    private void locateClass(String qualifiedClassName) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            ParamLibraryTreeModel.Node node = valueOf(child);
            if (node != null && Objects.equals(node.getClassName(), qualifiedClassName)) {
                TreePath path = new TreePath(child.getPath());
                tree.expandPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    private void selectionChanged(TreeSelectionEvent event) {
        ParamLibraryTreeModel.Node selected = singleSelectedPreset();
        if (selected == null) {
            previewToken.nextToken();
            preview.setText("选择一个参数方案以预览 JSON");
            return;
        }
        long token = previewToken.nextToken();
        preview.setText("正在加载...");
        DataContext.instance(project).findParamDataAsync(
                selected.getClassName(),
                selected.getPreset().getId(),
                item -> {
                    if (Disposer.isDisposed(this) || !previewToken.isCurrent(token)) {
                        return;
                    }
                    if (item == null) {
                        preview.setText("参数方案不存在或已被删除");
                        return;
                    }
                    preview.setText(JsonUtil.formatterJson(item.getParam()));
                    preview.setCaretPosition(0);
                }
        );
    }

    private void openSelectedPreset() {
        ParamLibraryTreeModel.Node selected = singleSelectedPreset();
        if (selected == null) {
            return;
        }
        WriteIntentReadAction.run((Runnable) () -> {
            PsiClass psiClass = IdeClassUtil.findClass(project, selected.getClassName());
            PsiMethod psiMethod = IdeClassUtil.findMethod(project, selected.getQualifiedMethodName());
            if (psiClass == null || psiMethod == null) {
                NotifierUtil.notifyError(project, "class or method not found");
                return;
            }
            AnyDoorSettingsState settings = project.getService(AnyDoorSettingsState.class);
            new AnyDoorPerformed().doUseNewUI(
                    settings,
                    project,
                    psiClass,
                    psiMethod,
                    AnyDoorActionUtil.genCacheKey(psiClass, psiMethod),
                    () -> AnyDoorAgainOpenAction.PRE_METHOD_MAP.put(project.getName(), psiMethod),
                    selected.getPreset().getId()
            );
        });
    }

    private void startRename() {
        TreePath path = tree.getSelectionPath();
        if (path != null && singleSelectedPreset() != null) {
            tree.startEditingAtPath(path);
        }
    }

    private void renamePreset(ParamLibraryTreeModel.Node node, String newName) {
        DataContext.instance(project).renameParamDataAsync(
                node.getClassName(),
                node.getQualifiedMethodName(),
                node.getPreset().getId(),
                newName,
                result -> handleMutationResult(result, "参数方案已重命名")
        );
    }

    private void deleteSelectedPresets() {
        List<ParamLibraryTreeModel.Node> nodes = selectedPresetNodes();
        if (nodes.isEmpty()) {
            return;
        }
        int answer = Messages.showYesNoDialog(
                project,
                "确定删除选中的 " + nodes.size() + " 个参数方案吗？",
                "删除参数方案",
                Messages.getQuestionIcon()
        );
        if (answer != Messages.YES) {
            return;
        }
        List<ParamIndexData> items = nodes.stream().map(ParamLibraryTreeModel.Node::getPreset).toList();
        DataContext.instance(project).deleteParamDataAsync(items, result -> handleMutationResult(result, "参数方案已删除"));
    }

    private void handleMutationResult(ParamMutationResult result, String successMessage) {
        if (!result.success()) {
            NotifierUtil.notifyError(project, result.message());
            return;
        }
        NotifierUtil.notifyInfo(project, successMessage);
        preview.setText("选择一个参数方案以预览 JSON");
        reloadIndex();
    }

    private void copyPreview() {
        if (singleSelectedPreset() != null && !preview.getText().isBlank()) {
            preview.selectAll();
            preview.copy();
            preview.select(0, 0);
            NotifierUtil.notifyInfo(project, "参数 JSON 已复制");
        }
    }

    private ParamLibraryTreeModel.Node singleSelectedPreset() {
        List<ParamLibraryTreeModel.Node> nodes = selectedPresetNodes();
        return nodes.size() == 1 ? nodes.get(0) : null;
    }

    private List<ParamLibraryTreeModel.Node> selectedPresetNodes() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) {
            return List.of();
        }
        List<ParamLibraryTreeModel.Node> result = new ArrayList<>();
        for (TreePath path : paths) {
            Object component = path.getLastPathComponent();
            if (component instanceof DefaultMutableTreeNode swingNode) {
                ParamLibraryTreeModel.Node node = valueOf(swingNode);
                if (node != null && node.getType() == ParamLibraryTreeModel.NodeType.PRESET) {
                    result.add(node);
                }
            }
        }
        return result;
    }

    private String selectedClassName() {
        VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
        return files.length == 0 ? null : classNameOf(files[0]);
    }

    private String classNameOf(VirtualFile file) {
        if (file == null) {
            return null;
        }
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                return null;
            }
            String packageName = javaFile.getPackageName();
            String simpleName = javaFile.getName().replaceFirst("\\.java$", "");
            return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        });
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == EventType.DATA_SYNC) {
            reloadIndex();
        }
    }

    @Override
    public void dispose() {
        DefaultMulticaster.getInstance(project).removeListener(this);
        ToolTipManager.sharedInstance().unregisterComponent(tree);
    }

    private static ParamLibraryTreeModel.Node valueOf(DefaultMutableTreeNode node) {
        Object value = node.getUserObject();
        return value instanceof ParamLibraryTreeModel.Node libraryNode ? libraryNode : null;
    }

    private final class LibraryTreeModel extends DefaultTreeModel {
        private LibraryTreeModel(DefaultMutableTreeNode root) {
            super(root);
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {
            Object component = path.getLastPathComponent();
            if (!(component instanceof DefaultMutableTreeNode swingNode)) {
                return;
            }
            ParamLibraryTreeModel.Node node = valueOf(swingNode);
            if (node != null && node.getType() == ParamLibraryTreeModel.NodeType.PRESET) {
                renamePreset(node, Objects.toString(newValue, ""));
            }
        }
    }

    private static final class PresetTreeCellEditor extends DefaultTreeCellEditor {
        private PresetTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
            super(tree, renderer);
        }

        @Override
        public boolean isCellEditable(EventObject event) {
            TreePath path = tree.getSelectionPath();
            if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode swingNode)) {
                return false;
            }
            ParamLibraryTreeModel.Node node = valueOf(swingNode);
            return node != null
                    && node.getType() == ParamLibraryTreeModel.NodeType.PRESET
                    && super.isCellEditable(event);
        }
    }

    private final class LibraryTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (!(value instanceof DefaultMutableTreeNode swingNode)) {
                return this;
            }
            ParamLibraryTreeModel.Node node = valueOf(swingNode);
            if (node == null) {
                return this;
            }
            setFont(tree.getFont());
            String text = node.getLabel();
            if (node.getType() == ParamLibraryTreeModel.NodeType.CLASS && node.getSecondaryText() != null) {
                text += "  —  " + node.getSecondaryText();
                setToolTipText(node.getClassName());
                if (Objects.equals(node.getClassName(), activeClassName)) {
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            } else {
                setToolTipText(node.getQualifiedMethodName());
            }
            setText(text);
            return this;
        }
    }
}
