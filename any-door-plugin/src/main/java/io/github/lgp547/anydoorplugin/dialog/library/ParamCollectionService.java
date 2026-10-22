package io.github.lgp547.anydoorplugin.dialog.library;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.github.lgp547.anydoorplugin.data.domain.ParamCollection;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service(Service.Level.PROJECT)
public final class ParamCollectionService {
    public static final String UNGROUPED = "__ungrouped__";
    private final AnyDoorSettingsState settings;
    public ParamCollectionService(Project project) { settings = project.getService(AnyDoorSettingsState.class); }
    public static ParamCollectionService instance(Project project) { return project.getService(ParamCollectionService.class); }
    public List<ParamCollection> list() { return new ArrayList<>(settings.paramCollections); }
    public ParamCollection create(String name) { ParamCollection value = new ParamCollection(UUID.randomUUID().toString(), name.trim()); settings.paramCollections.add(value); return value; }
    public void rename(String id, String name) { find(id).setName(name.trim()); }
    public void delete(String id) { settings.paramCollections.removeIf(value -> value.getId().equals(id)); }
    public void moveMethod(String methodName, String collectionId) {
        settings.paramCollections.forEach(value -> value.getMethodNames().remove(methodName));
        if (!UNGROUPED.equals(collectionId)) find(collectionId).getMethodNames().add(methodName);
    }
    public String collectionOf(String methodName) { return settings.paramCollections.stream().filter(value -> value.getMethodNames().contains(methodName)).map(ParamCollection::getId).findFirst().orElse(UNGROUPED); }
    private ParamCollection find(String id) { return settings.paramCollections.stream().filter(value -> value.getId().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Collection not found")); }
}
