package io.github.lgp547.anydoorplugin.request;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/** .anydoor is a lightweight language without a PSI parser, so gutters are document based. */
@Service(Service.Level.PROJECT)
public final class AnyDoorRequestGutterService implements FileEditorManagerListener {
    private static final Key<List<RangeHighlighter>> MARKERS = Key.create("anydoor.request.gutter.markers");
    private final Project project;
    public AnyDoorRequestGutterService(Project project) {
        this.project = project;
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(@org.jetbrains.annotations.NotNull DocumentEvent event) {
                VirtualFile file = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(event.getDocument());
                if (file != null && "anydoor".equalsIgnoreCase(file.getExtension())) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> install(event.getDocument()));
                }
            }
        }, project);
    }
    public void installOpenFiles() { for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) fileOpened(FileEditorManager.getInstance(project), file); }
    @Override public void fileOpened(FileEditorManager source, VirtualFile file) {
        if (!"anydoor".equalsIgnoreCase(file.getExtension())) return;
        Document document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return;
        install(document);
    }
    private void install(Document document) {
        List<RangeHighlighter> old = document.getUserData(MARKERS);
        if (old != null) old.forEach(marker -> { if (marker.isValid()) marker.dispose(); });
        List<RangeHighlighter> markers = new ArrayList<>();
        document.putUserData(MARKERS, markers);
        for (int line = 0; line < document.getLineCount(); line++) {
            int start = document.getLineStartOffset(line), end = document.getLineEndOffset(line);
            if (!document.getText(new com.intellij.openapi.util.TextRange(start, end)).trim().startsWith("RUN ")) continue;
            RangeHighlighter marker = DocumentMarkupModel.forDocument(document, project, true).addRangeHighlighter(start, start, HighlighterLayer.ADDITIONAL_SYNTAX, (TextAttributes)null, com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE);
            markers.add(marker);
            marker.setGutterIconRenderer(new GutterIconRenderer() {
                @Override public Icon getIcon() { return AllIcons.Actions.Execute; }
                @Override public String getTooltipText() { return "Run AnyDoor request"; }
                @Override public com.intellij.openapi.actionSystem.AnAction getClickAction() { return new com.intellij.openapi.actionSystem.AnAction() { @Override public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent e) { AnyDoorRequestRunner.runAt(project, document.getText(), start); } }; }
                @Override public boolean equals(Object other) { return this == other; }
                @Override public int hashCode() { return start; }
            });
        }
    }
}
