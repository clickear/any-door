package io.github.lgp547.anydoorplugin.result;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ToolWindowManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class InvocationResultService {
    private static final int LIMIT = 100;
    private final Map<String, InvocationResult> results = new LinkedHashMap<>();
    private final List<Consumer<List<InvocationResult>>> listeners = new CopyOnWriteArrayList<>();
    private final Project project;

    public InvocationResultService(Project project) { this.project = project; }

    public static InvocationResultService instance(Project project) {
        return project.getService(InvocationResultService.class);
    }

    public String begin(String invocation, String target) {
        String id = UUID.randomUUID().toString();
        synchronized (results) {
            results.put(id, InvocationResult.running(id, invocation, target));
            trim();
        }
        publish();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            var window = ToolWindowManager.getInstance(project).getToolWindow("Any-Door");
            if (window == null) return;
            window.show(() -> {
                var content = window.getContentManager().findContent("Results");
                if (content != null) window.getContentManager().setSelectedContent(content);
            });
        });
        return id;
    }

    public void success(String id, String json) { update(id, value -> value.success(json)); }
    public void failure(String id, Throwable error) { update(id, value -> value.failure(error)); }

    public List<InvocationResult> list() {
        synchronized (results) {
            ArrayList<InvocationResult> copy = new ArrayList<>(results.values());
            java.util.Collections.reverse(copy);
            return copy;
        }
    }

    public void clear() {
        synchronized (results) { results.clear(); }
        publish();
    }

    public AutoCloseable listen(Consumer<List<InvocationResult>> listener) {
        listeners.add(listener);
        listener.accept(list());
        return () -> listeners.remove(listener);
    }

    private void update(String id, java.util.function.UnaryOperator<InvocationResult> operation) {
        synchronized (results) {
            InvocationResult current = results.get(id);
            if (current != null) results.put(id, operation.apply(current));
        }
        publish();
    }

    private void trim() {
        while (results.size() > LIMIT) results.remove(results.keySet().iterator().next());
    }

    private void publish() {
        List<InvocationResult> snapshot = list();
        listeners.forEach(listener -> listener.accept(snapshot));
    }
}
