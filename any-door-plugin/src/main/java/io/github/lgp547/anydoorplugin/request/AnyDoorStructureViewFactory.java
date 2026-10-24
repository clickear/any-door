package io.github.lgp547.anydoorplugin.request;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File Structure / Cmd+F12 entries for named AnyDoor request blocks. */
public final class AnyDoorStructureViewFactory implements PsiStructureViewFactory {
    @Override public @NotNull StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile file) {
        return new TreeBasedStructureViewBuilder() {
            @Override public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new TextEditorBasedStructureViewModel(editor, file) {
                    private final StructureViewTreeElement root = new RequestElement(file, null, 0);
                    @Override public @NotNull StructureViewTreeElement getRoot() { return root; }
                    @Override protected Class<?> @NotNull [] getSuitableClasses() { return new Class[]{RequestElement.class}; }
                };
            }
        };
    }

    private record Request(String name, String target, int offset) { }

    private static final class RequestElement implements StructureViewTreeElement, ItemPresentation {
        private static final Pattern TITLE = Pattern.compile("(?m)^###\\s*(.*)$");
        private static final Pattern RUN = Pattern.compile("(?m)^\\s*RUN\\s+([^\\s]+)");
        private final PsiFile file;
        private final Request request;
        private final int rootOffset;
        private RequestElement(PsiFile file, Request request, int rootOffset) { this.file = file; this.request = request; this.rootOffset = rootOffset; }
        @Override public Object getValue() { return request == null ? file : request; }
        @Override public @NotNull ItemPresentation getPresentation() { return this; }
        @Override public StructureViewTreeElement @NotNull [] getChildren() {
            if (request != null) return EMPTY_ARRAY;
            List<Request> requests = requests(file.getText());
            return requests.stream().map(item -> new RequestElement(file, item, 0)).toArray(StructureViewTreeElement[]::new);
        }
        @Override public String getPresentableText() { return request == null ? file.getName() : request.name(); }
        @Override public String getLocationString() { return request == null || request.target().isBlank() ? null : request.target(); }
        @Override public Icon getIcon(boolean unused) { return AnyDoorRequestFileType.INSTANCE.getIcon(); }
        @Override public void navigate(boolean requestFocus) { if (canNavigate()) new OpenFileDescriptor(file.getProject(), file.getVirtualFile(), request.offset()).navigate(requestFocus); }
        @Override public boolean canNavigate() { return request != null && file.getVirtualFile() != null; }
        @Override public boolean canNavigateToSource() { return canNavigate(); }
        private static List<Request> requests(String text) {
            List<Request> result = new ArrayList<>();
            Matcher titles = TITLE.matcher(text);
            List<Integer> starts = new ArrayList<>();
            List<String> names = new ArrayList<>();
            while (titles.find()) { starts.add(titles.start()); names.add(titles.group(1).isBlank() ? "Untitled request" : titles.group(1).trim()); }
            if (starts.isEmpty()) starts.add(0);
            if (names.isEmpty()) names.add("Request");
            for (int index = 0; index < starts.size(); index++) {
                int start = starts.get(index);
                int end = index + 1 < starts.size() ? starts.get(index + 1) : text.length();
                Matcher run = RUN.matcher(text.substring(start, end));
                String target = run.find() ? run.group(1) : "";
                result.add(new Request(names.get(index), target, start));
            }
            return result;
        }
    }
}
