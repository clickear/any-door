package io.github.lgp547.anydoorplugin.dialog;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.lgp547.anydoorplugin.data.DataService;
import io.github.lgp547.anydoorplugin.data.domain.Data;
import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import io.github.lgp547.anydoorplugin.data.impl.ParamDataService;
import io.github.lgp547.anydoorplugin.data.impl.ParamIndexService;
import io.github.lgp547.anydoorplugin.dialog.perf.ParamIndexSearchEngine;
import io.github.lgp547.anydoorplugin.dialog.event.Event;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.event.DefaultMulticaster;
import io.github.lgp547.anydoorplugin.dialog.event.Listener;
import io.github.lgp547.anydoorplugin.dialog.event.impl.GlobalDataChangeEvent;
import io.github.lgp547.anydoorplugin.dialog.library.ParamLibraryDataUpdater;
import io.github.lgp547.anydoorplugin.dialog.library.ParamMutationResult;
import io.github.lgp547.anydoorplugin.dialog.utils.EventHelper;
import io.github.lgp547.anydoorplugin.dialog.utils.IdeClassUtil;
import io.github.lgp547.anydoorplugin.dto.ParamCacheDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: zhouh
 * @date: 2023-07-18 18:09
 **/
@Service(value = Service.Level.PROJECT)
public final class DataContext implements Listener {

    private final Project project;
    private final DataService<ParamIndexData> indexService;
    private final DataService<ParamDataItem> dataService;


    private final Map<String, ClassDataContext> contextMap;
    private final Data<ParamIndexData> indexData;
    private final ParamIndexSearchEngine searchEngine;

    public static DataContext instance(Project project) {
        return project.getService(DataContext.class);
    }

    public DataContext(Project project) {
        this.project = project;
        this.indexService = project.getService(ParamIndexService.class);
        this.dataService = project.getService(ParamDataService.class);
        this.contextMap = new ConcurrentHashMap<>();
        DefaultMulticaster.getInstance(project).setDataChangeListener(this);

        indexData = indexService.find(project.getName());
        searchEngine = new ParamIndexSearchEngine();
        rebuildSearchEngine();
    }

    public ClassDataContext getClassDataContext(String qualifiedClassName) {
        Objects.requireNonNull(qualifiedClassName);

        ClassDataContext classDataContext = contextMap.get(qualifiedClassName);
        if (classDataContext == null || classDataContext.clazz == null || !classDataContext.clazz.isValid()) {
            ClassDataContext context = buildClassDataContext(qualifiedClassName, false);
            contextMap.put(qualifiedClassName, context);
            return context;
        }
        return classDataContext;
    }

    public void getClassDataContextAsync(String qualifiedClassName, Consumer<ClassDataContext> consumer) {
        Objects.requireNonNull(consumer);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ClassDataContext context = getClassDataContext(qualifiedClassName);
            ApplicationManager.getApplication().invokeLater(() -> consumer.accept(context));
        });
    }

    public ClassDataContext getClassDataContextNoCache(String qualifiedClassName) {
        ClassDataContext context = buildClassDataContext(qualifiedClassName, true);
        contextMap.put(qualifiedClassName, context);
        return context;
    }

    public void getClassDataContextNoCacheAsync(String qualifiedClassName, Consumer<ClassDataContext> consumer) {
        Objects.requireNonNull(consumer);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ClassDataContext context = getClassDataContextNoCache(qualifiedClassName);
            ApplicationManager.getApplication().invokeLater(() -> consumer.accept(context));
        });
    }

//    public MethodDataContext getExecuteDataContext(String qualifiedClassName, String qualifiedMethodName, ParamCacheDto cache) {
//        return getExecuteDataContext(qualifiedClassName, qualifiedMethodName, null, cache.content());
//    }

    public MethodDataContext getExecuteDataContext(String qualifiedClassName, String qualifiedMethodName, Long selectedId, String cacheContent) {
        Objects.requireNonNull(qualifiedClassName);
        Objects.requireNonNull(qualifiedMethodName);

        ClassDataContext classDataContext = getClassDataContext(qualifiedClassName);

        return classDataContext.newMethodDataContext(qualifiedMethodName, selectedId, cacheContent);
    }

    public void getExecuteDataContextAsync(String qualifiedClassName,
                                           String qualifiedMethodName,
                                           Long selectedId,
                                           String cacheContent,
                                           Consumer<MethodDataContext> consumer) {
        Objects.requireNonNull(consumer);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            MethodDataContext context = getExecuteDataContext(qualifiedClassName, qualifiedMethodName, selectedId, cacheContent);
            ApplicationManager.getApplication().invokeLater(() -> consumer.accept(context));
        });
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof GlobalDataChangeEvent) {
            GlobalDataChangeEvent changeEvent = (GlobalDataChangeEvent) event;

            if (Objects.equals(event.getType(), EventType.GLOBAL_SAVE_DATA_CHANGE)) {
                contextMap.computeIfPresent(changeEvent.getQualifiedClassName(), (k, v) -> {
                    v.addItems(changeEvent.getDataItems());
                    dataService.save(v.data);

                    List<ParamIndexData> dataList = changeEvent.getDataItems().stream().map(ParamDataItem::toIndexData).collect(Collectors.toList());
                    indexData.getDataList().addAll(dataList);
                    indexService.save(indexData);
                    rebuildSearchEngine();
                    return v;
                });


            } else if (Objects.equals(event.getType(), EventType.GLOBAL_DELETE_DATA_CHANGE)) {
                contextMap.computeIfPresent(changeEvent.getQualifiedClassName(), (k, v) -> {
                    v.removeItems(changeEvent.getDataItems());
                    dataService.save(v.data);

                    Set<Long> idSet = changeEvent.getDataItems().stream().map(ParamDataItem::getId).collect(Collectors.toSet());
                    indexData.setDataList(indexData.getDataList().stream().filter(item -> !idSet.contains(item.getId())).collect(Collectors.toList()));
                    indexService.save(indexData);
                    rebuildSearchEngine();
                    return v;
                });

            } else if (Objects.equals(event.getType(), EventType.GLOBAL_UPDATE_DATA_CHANGE)) {
                contextMap.computeIfPresent(changeEvent.getQualifiedClassName(), (k, v) -> {

                    List<ParamIndexData> dataList = v.data.getDataList().stream()
                            .filter(item -> Objects.equals(item.getQualifiedName(), changeEvent.getQualifiedMethodName()))
                            .map(ParamDataItem::toIndexData)
                            .collect(Collectors.toList());
                    indexData.getDataList().removeIf(item -> Objects.equals(item.getQualifiedMethodName(), changeEvent.getQualifiedMethodName()));

                    indexData.getDataList().addAll(dataList);
                    indexService.save(indexData);
                    rebuildSearchEngine();
                    return v;
                });
            }
            DefaultMulticaster.getInstance(project).fireEvent(EventHelper.createDataSyncEvent(changeEvent.getQualifiedMethodName()));
        }
    }

    private static final int SEARCH_LIMIT = 200;

    public List<ParamIndexData> search(String text) {
        return searchEngine.search(text, SEARCH_LIMIT);
    }

    public void searchAsync(String text, Consumer<List<ParamIndexData>> consumer) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<ParamIndexData> result = search(text);
            ApplicationManager.getApplication().invokeLater(() -> consumer.accept(result));
        });
    }

    public List<ParamIndexData> listParamIndexData() {
        synchronized (indexData.getDataList()) {
            return indexData.getDataList().stream()
                    .map(DataContext::copyIndexData)
                    .collect(Collectors.toList());
        }
    }

    public void listParamIndexDataAsync(Consumer<List<ParamIndexData>> consumer) {
        Objects.requireNonNull(consumer);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<ParamIndexData> result = listParamIndexData();
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    consumer.accept(result);
                }
            });
        });
    }

    public void findParamDataAsync(String qualifiedClassName, Long id, Consumer<ParamDataItem> consumer) {
        Objects.requireNonNull(qualifiedClassName);
        Objects.requireNonNull(id);
        Objects.requireNonNull(consumer);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ParamDataItem result = findParamData(qualifiedClassName, id);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    consumer.accept(result);
                }
            });
        });
    }

    public void renameParamDataAsync(String qualifiedClassName,
                                     String qualifiedMethodName,
                                     Long id,
                                     String newName,
                                     Consumer<ParamMutationResult> consumer) {
        Objects.requireNonNull(consumer);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ParamMutationResult result = renameParamData(qualifiedClassName, qualifiedMethodName, id, newName);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    if (result.success()) {
                        DefaultMulticaster.getInstance(project).fireEvent(EventHelper.createDataSyncEvent(qualifiedMethodName));
                    }
                    consumer.accept(result);
                }
            });
        });
    }

    public void deleteParamDataAsync(Collection<ParamIndexData> items, Consumer<ParamMutationResult> consumer) {
        Objects.requireNonNull(consumer);
        List<ParamIndexData> snapshot = items == null
                ? List.of()
                : items.stream().filter(Objects::nonNull).map(DataContext::copyIndexData).collect(Collectors.toList());
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ParamMutationResult result = deleteParamData(snapshot);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    if (result.success()) {
                        snapshot.stream()
                                .map(ParamIndexData::getQualifiedMethodName)
                                .filter(Objects::nonNull)
                                .distinct()
                                .forEach(method -> DefaultMulticaster.getInstance(project)
                                        .fireEvent(EventHelper.createDataSyncEvent(method)));
                    }
                    consumer.accept(result);
                }
            });
        });
    }

    private void rebuildSearchEngine() {
        searchEngine.rebuild(indexData.getDataList());
    }

    private synchronized ParamDataItem findParamData(String qualifiedClassName, Long id) {
        Data<ParamDataItem> data = mutableClassData(qualifiedClassName);
        return data.getDataList().stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .map(DataContext::copyParamData)
                .orElse(null);
    }

    private synchronized ParamMutationResult renameParamData(String qualifiedClassName,
                                                             String qualifiedMethodName,
                                                             Long id,
                                                             String newName) {
        Data<ParamDataItem> data = mutableClassData(qualifiedClassName);
        synchronized (data.getDataList()) {
            synchronized (indexData.getDataList()) {
                ParamMutationResult result = ParamLibraryDataUpdater.rename(
                        data.getDataList(), indexData.getDataList(), qualifiedMethodName, id, newName
                );
                if (!result.success()) {
                    return result;
                }
                dataService.save(data);
                indexService.save(indexData);
                rebuildSearchEngine();
            }
        }
        return ParamMutationResult.ok();
    }

    private synchronized ParamMutationResult deleteParamData(Collection<ParamIndexData> items) {
        if (items == null || items.isEmpty()) {
            return ParamMutationResult.failure("请选择要删除的参数方案");
        }

        Map<String, List<ParamIndexData>> byClass = items.stream()
                .filter(item -> classNameOf(item.getQualifiedMethodName()) != null)
                .collect(Collectors.groupingBy(item -> classNameOf(item.getQualifiedMethodName())));
        if (byClass.isEmpty()) {
            return ParamMutationResult.failure("参数方案缺少方法信息");
        }

        Set<Long> ids = items.stream().map(ParamIndexData::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        byClass.forEach((className, classItems) -> {
            Data<ParamDataItem> data = mutableClassData(className);
            synchronized (data.getDataList()) {
                ParamLibraryDataUpdater.removeDataItems(
                        data.getDataList(),
                        classItems.stream().map(ParamIndexData::getId).collect(Collectors.toSet())
                );
                dataService.save(data);
            }
        });

        synchronized (indexData.getDataList()) {
            ParamLibraryDataUpdater.removeIndexItems(indexData.getDataList(), ids);
            indexService.save(indexData);
            rebuildSearchEngine();
        }
        return ParamMutationResult.ok();
    }

    private Data<ParamDataItem> mutableClassData(String qualifiedClassName) {
        ClassDataContext cached = contextMap.get(qualifiedClassName);
        return cached == null ? dataService.find(qualifiedClassName) : cached.data;
    }

    private static String classNameOf(String qualifiedMethodName) {
        if (qualifiedMethodName == null) {
            return null;
        }
        int separator = qualifiedMethodName.lastIndexOf('#');
        return separator <= 0 ? null : qualifiedMethodName.substring(0, separator);
    }

    private static ParamIndexData copyIndexData(ParamIndexData source) {
        ParamIndexData copy = new ParamIndexData();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setQualifiedMethodName(source.getQualifiedMethodName());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setDeleted(source.getDeleted());
        return copy;
    }

    private static ParamDataItem copyParamData(ParamDataItem source) {
        ParamDataItem copy = new ParamDataItem(source.getName(), source.getQualifiedName(), source.getParam());
        copy.setId(source.getId());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setDeleted(source.getDeleted());
        return copy;
    }

    private ClassDataContext buildClassDataContext(String qualifiedClassName, boolean noCache) {
        Objects.requireNonNull(qualifiedClassName);

        Data<ParamDataItem> data = noCache
                ? dataService.findNoCache(qualifiedClassName)
                : dataService.find(qualifiedClassName);
        PsiClass psiClass = ReadAction.compute(() -> noCache
                ? JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, GlobalSearchScope.allScope(project))
                : IdeClassUtil.findClass(project, qualifiedClassName));
        return new ClassDataContext(psiClass, data, project);
    }
}
