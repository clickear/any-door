package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ephemeral results deliberately stay out of the request source file and VCS. */
@Service(Service.Level.PROJECT)
public final class RequestResultService {
    private final Map<String, String> results = new ConcurrentHashMap<>();
    public static RequestResultService instance(Project project) { return project.getService(RequestResultService.class); }
    public void put(String name, String value) { results.put(name, value); }
    public String get(String name) { return results.get(name); }
}
