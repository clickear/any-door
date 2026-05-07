# Plugin Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变插件功能和持久化格式的前提下，把参数列表、搜索弹窗和方法执行弹窗中的同步重活移出 EDT，降低整体卡顿。

**Architecture:** 方案分两层推进。第一层先抽出可测试的纯逻辑组件，包括最新请求令牌、刷新请求状态和搜索引擎；第二层再把 `DataContext`、`ParamListUI`、`MainUI` 和入口动作改成“后台 prepare，EDT apply”的异步刷新模型，并对快速切换和搜索输入做防抖与过期结果抑制。

**Tech Stack:** Java 17, Gradle Kotlin DSL, IntelliJ Platform SDK, Swing, JUnit 5

---

## File Map

- `any-door-plugin/build.gradle.kts`
  作用：补齐 JUnit 5 测试依赖和 `useJUnitPlatform()`。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestToken.java`
  作用：为异步刷新和异步搜索提供“仅最新请求可生效”的令牌判断。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestState.java`
  作用：记录当前刷新 key 和 token，抑制重复刷新并判定结果是否过期。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngine.java`
  作用：维护标准化后的索引快照，提供大小写不敏感、可限制数量的搜索。
- `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestTokenTest.java`
  作用：验证最新请求令牌逻辑。
- `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestStateTest.java`
  作用：验证刷新请求去重和过期判断逻辑。
- `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngineTest.java`
  作用：验证搜索标准化、大小写不敏感和结果上限。
- `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamDataItemIndexConversionTest.java`
  作用：验证参数项转索引数据时不会丢失搜索字段。
- `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestStateAdvancedTest.java`
  作用：验证重复刷新抑制和跨 key 的 token 失效逻辑。
- `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestTokenDialogUsageTest.java`
  作用：验证弹窗异步装载同样遵守“仅最后一次请求生效”。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java`
  作用：增加异步加载 `ClassDataContext` / `MethodDataContext` 的 API，并维护搜索引擎快照。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/ParamListUI.java`
  作用：把文件切换刷新改成异步且可合并；把搜索改成防抖和异步过滤。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/MainUI.java`
  作用：支持方法弹窗先显示容器，再异步绑定 `MethodDataContext`。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/action/AnyDoorPerformed.java`
  作用：把新 UI 入口改成异步加载上下文。
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/event/DefaultMulticaster.java`
  作用：同步分发语义不变，但迭代监听器快照，减少监听器列表变更带来的问题。

### Task 1: 搭建性能优化的可测试基础

**Files:**
- Create: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestToken.java`
- Create: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestState.java`
- Create: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngine.java`
- Create: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestTokenTest.java`
- Create: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestStateTest.java`
- Create: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngineTest.java`
- Modify: `any-door-plugin/build.gradle.kts`

- [ ] **Step 1: 给插件工程补齐 JUnit 5 测试支持**

```kotlin
dependencies {
    implementation("io.github.lgp547:any-door-core:$anyDoorVersion")
    implementation("io.github.lgp547:any-door-attach:$anyDoorVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
}
```

- [ ] **Step 2: 先写失败测试，锁定请求令牌、刷新去重和搜索行为**

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatestRequestTokenTest {

    @Test
    void keepsOnlyNewestTokenCurrent() {
        LatestRequestToken token = new LatestRequestToken();

        long first = token.nextToken();
        long second = token.nextToken();

        assertFalse(token.isCurrent(first));
        assertTrue(token.isCurrent(second));
    }
}
```

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshRequestStateTest {

    @Test
    void skipsDuplicateKeyUntilKeyChanges() {
        RefreshRequestState state = new RefreshRequestState();

        assertTrue(state.shouldSchedule("demo.Service"));
        assertFalse(state.shouldSchedule("demo.Service"));
        assertTrue(state.shouldSchedule("demo.OtherService"));
    }

    @Test
    void acceptsOnlyLatestTokenForCurrentKey() {
        RefreshRequestState state = new RefreshRequestState();

        long first = state.markScheduled("demo.Service");
        long second = state.markScheduled("demo.OtherService");

        assertFalse(state.isLatest("demo.Service", first));
        assertTrue(state.isLatest("demo.OtherService", second));
    }
}
```

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamIndexSearchEngineTest {

    @Test
    void returnsEmptyWhenKeywordIsBlank() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(sample("SaveUser", "demo.UserService#save(java.lang.String)")));

        assertTrue(engine.search("", 20).isEmpty());
    }

    @Test
    void matchesNameAndQualifiedMethodCaseInsensitively() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(
                sample("SaveUser", "demo.UserService#save(java.lang.String)"),
                sample("DeleteUser", "demo.UserService#delete(java.lang.Long)")
        ));

        assertEquals(1, engine.search("saveuser", 20).size());
        assertEquals(1, engine.search("DELETE(JAVA.LANG.LONG)", 20).size());
    }

    @Test
    void respectsResultLimit() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(
                sample("A1", "demo.S#run1()"),
                sample("A2", "demo.S#run2()"),
                sample("A3", "demo.S#run3()")
        ));

        assertEquals(2, engine.search("run", 2).size());
    }

    private static ParamIndexData sample(String name, String qualifiedMethodName) {
        ParamIndexData data = new ParamIndexData();
        data.setName(name);
        data.setQualifiedMethodName(qualifiedMethodName);
        return data;
    }
}
```

- [ ] **Step 3: 运行测试，确认当前实现还不存在**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.*" -v`  
Workdir: `any-door-plugin`  
Expected: FAIL，报错包含 `cannot find symbol` 或 `ClassNotFoundException`

- [ ] **Step 4: 只实现最小性能基础类，让测试先通过**

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import java.util.concurrent.atomic.AtomicLong;

public final class LatestRequestToken {

    private final AtomicLong sequence = new AtomicLong();

    public long nextToken() {
        return sequence.incrementAndGet();
    }

    public boolean isCurrent(long token) {
        return sequence.get() == token;
    }
}
```

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import java.util.Objects;

public final class RefreshRequestState {

    private final LatestRequestToken latestRequestToken = new LatestRequestToken();
    private volatile String currentKey;

    public boolean shouldSchedule(String key) {
        return !Objects.equals(currentKey, key);
    }

    public long markScheduled(String key) {
        currentKey = key;
        return latestRequestToken.nextToken();
    }

    public boolean isLatest(String key, long token) {
        return Objects.equals(currentKey, key) && latestRequestToken.isCurrent(token);
    }

    public String getCurrentKey() {
        return currentKey;
    }
}
```

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ParamIndexSearchEngine {

    private volatile List<SearchEntry> entries = List.of();

    public void rebuild(List<ParamIndexData> source) {
        List<SearchEntry> next = new ArrayList<>();
        if (source != null) {
            for (ParamIndexData item : source) {
                next.add(new SearchEntry(item));
            }
        }
        this.entries = List.copyOf(next);
    }

    public List<ParamIndexData> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalized = keyword.toLowerCase(Locale.ROOT);
        List<ParamIndexData> result = new ArrayList<>();
        for (SearchEntry entry : entries) {
            if (entry.matches(normalized)) {
                result.add(entry.source());
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private record SearchEntry(ParamIndexData source, String lowerName, String lowerQualifiedMethodName) {
        private SearchEntry(ParamIndexData source) {
            this(
                    source,
                    normalize(source == null ? null : source.getName()),
                    normalize(source == null ? null : source.getQualifiedMethodName())
            );
        }

        private boolean matches(String keyword) {
            return lowerName.contains(keyword) || lowerQualifiedMethodName.contains(keyword);
        }

        private static String normalize(String value) {
            return Objects.toString(value, "").toLowerCase(Locale.ROOT);
        }
    }
}
```

- [ ] **Step 5: 再跑一次测试，确认纯逻辑行为稳定**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.*" -v`  
Workdir: `any-door-plugin`  
Expected: PASS，输出包含 `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交这一层基础设施**

```bash
git add any-door-plugin/build.gradle.kts \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestToken.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestState.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngine.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestTokenTest.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestStateTest.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngineTest.java
git commit -m "test: add performance helper coverage"
```

### Task 2: 给 DataContext 增加异步上下文加载和搜索索引缓存

**Files:**
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/data/domain/ParamDataItem.java`
- Create: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamDataItemIndexConversionTest.java`

- [ ] **Step 1: 先补一个回归测试，锁定索引数据构建时需要保留名称和方法限定名**

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParamDataItemIndexConversionTest {

    @Test
    void convertsToIndexDataWithoutLosingSearchFields() {
        ParamDataItem item = new ParamDataItem("SaveUser", "demo.UserService#save(java.lang.String)", "{}");
        item.setId(12L);

        ParamIndexData indexData = item.toIndexData();

        assertEquals(12L, indexData.getId());
        assertEquals("SaveUser", indexData.getName());
        assertEquals("demo.UserService#save(java.lang.String)", indexData.getQualifiedMethodName());
    }
}
```

- [ ] **Step 2: 运行这个测试，确认当前行为被显式覆盖**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.ParamDataItemIndexConversionTest" -v`  
Workdir: `any-door-plugin`  
Expected: PASS。这个测试是回归保护，后续修改搜索缓存时不能破坏已有数据转换。

- [ ] **Step 3: 在 DataContext 中增加后台加载 API，并维护搜索引擎快照**

```java
package io.github.lgp547.anydoorplugin.dialog;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.lgp547.anydoorplugin.dialog.perf.ParamIndexSearchEngine;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class DataContext implements Listener {

    private final ExecutorService backgroundExecutor = AppExecutorUtil.getAppExecutorService();
    private final ParamIndexSearchEngine searchEngine = new ParamIndexSearchEngine();

    public DataContext(Project project) {
        this.project = project;
        this.indexService = project.getService(ParamIndexService.class);
        this.dataService = project.getService(ParamDataService.class);
        this.contextMap = new ConcurrentHashMap<>();
        DefaultMulticaster.getInstance(project).setDataChangeListener(this);
        indexData = indexService.find(project.getName());
        rebuildSearchIndex();
    }

    public void loadClassDataContextAsync(String qualifiedClassName, Consumer<ClassDataContext> consumer) {
        Objects.requireNonNull(qualifiedClassName);
        Objects.requireNonNull(consumer);

        backgroundExecutor.execute(() -> {
            Data<ParamDataItem> data = dataService.find(qualifiedClassName);
            PsiClass psiClass = ReadAction.compute(() -> IdeClassUtil.findClass(project, qualifiedClassName));
            ClassDataContext context = new ClassDataContext(psiClass, data, project);
            contextMap.put(qualifiedClassName, context);
            if (!project.isDisposed()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) {
                        consumer.accept(context);
                    }
                });
            }
        });
    }

    public void loadExecuteDataContextAsync(String qualifiedClassName, String qualifiedMethodName, Long selectedId, String cacheContent,
                                            Consumer<MethodDataContext> consumer) {
        loadClassDataContextAsync(qualifiedClassName, classDataContext ->
                consumer.accept(classDataContext.newMethodDataContext(qualifiedMethodName, selectedId, cacheContent)));
    }

    public List<ParamIndexData> search(String text, int limit) {
        return searchEngine.search(text, limit);
    }

    private void rebuildSearchIndex() {
        searchEngine.rebuild(indexData.getDataList());
    }
}
```

- [ ] **Step 4: 在索引更新的三个分支中重建搜索引擎，避免搜索继续扫旧快照**

```java
if (Objects.equals(event.getType(), EventType.GLOBAL_SAVE_DATA_CHANGE)) {
    contextMap.computeIfPresent(changeEvent.getQualifiedClassName(), (k, v) -> {
        v.addItems(changeEvent.getDataItems());
        dataService.save(v.data);
        List<ParamIndexData> dataList = changeEvent.getDataItems().stream().map(ParamDataItem::toIndexData).collect(Collectors.toList());
        indexData.getDataList().addAll(dataList);
        indexService.save(indexData);
        rebuildSearchIndex();
        return v;
    });
}
```

```java
else if (Objects.equals(event.getType(), EventType.GLOBAL_DELETE_DATA_CHANGE)) {
    contextMap.computeIfPresent(changeEvent.getQualifiedClassName(), (k, v) -> {
        v.removeItems(changeEvent.getDataItems());
        dataService.save(v.data);
        Set<Long> idSet = changeEvent.getDataItems().stream().map(ParamDataItem::getId).collect(Collectors.toSet());
        indexData.setDataList(indexData.getDataList().stream().filter(item -> !idSet.contains(item.getId())).collect(Collectors.toList()));
        indexService.save(indexData);
        rebuildSearchIndex();
        return v;
    });
}
```

```java
else if (Objects.equals(event.getType(), EventType.GLOBAL_UPDATE_DATA_CHANGE)) {
    contextMap.computeIfPresent(changeEvent.getQualifiedClassName(), (k, v) -> {
        List<ParamIndexData> dataList = v.data.getDataList().stream().map(ParamDataItem::toIndexData).collect(Collectors.toList());
        indexData.getDataList().removeIf(item -> Objects.equals(item.getQualifiedMethodName(), changeEvent.getQualifiedMethodName()));
        indexData.getDataList().addAll(dataList);
        indexService.save(indexData);
        rebuildSearchIndex();
        return v;
    });
}
```

- [ ] **Step 5: 保持 `ParamDataItem.toIndexData()` 简单稳定，不把派生字段写入持久化结构**

```java
public ParamIndexData toIndexData() {
    ParamIndexData indexData = new ParamIndexData();
    indexData.setId(this.id);
    indexData.setName(this.name);
    indexData.setQualifiedMethodName(this.qualifiedName);
    return indexData;
}
```

- [ ] **Step 6: 编译主代码并回归已有纯逻辑测试**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.*" compileJava -v`  
Workdir: `any-door-plugin`  
Expected: PASS，输出包含 `Task :compileJava` 和 `BUILD SUCCESSFUL`

- [ ] **Step 7: 提交 DataContext 异步化和搜索缓存改动**

```bash
git add any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/data/domain/ParamDataItem.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamDataItemIndexConversionTest.java
git commit -m "refactor: add async context loading primitives"
```

### Task 3: 把参数列表刷新改成异步且可合并

**Files:**
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/ParamListUI.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestState.java`
- Create: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestStateAdvancedTest.java`

- [ ] **Step 1: 先补一个失败测试，锁定重复 key 不应重复刷新、换 key 后旧 token 失效**

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshRequestStateAdvancedTest {

    @Test
    void duplicateKeyDoesNotNeedAnotherSchedule() {
        RefreshRequestState state = new RefreshRequestState();
        assertTrue(state.shouldSchedule("demo.A"));
        state.markScheduled("demo.A");
        assertFalse(state.shouldSchedule("demo.A"));
    }

    @Test
    void olderTokenBecomesInvalidAfterNewKeyIsScheduled() {
        RefreshRequestState state = new RefreshRequestState();
        long first = state.markScheduled("demo.A");
        long second = state.markScheduled("demo.B");
        assertFalse(state.isLatest("demo.A", first));
        assertTrue(state.isLatest("demo.B", second));
    }
}
```

- [ ] **Step 2: 跑测试，确认去重规则先被约束住**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.RefreshRequestStateAdvancedTest" -v`  
Workdir: `any-door-plugin`  
Expected: PASS 或与现有 `RefreshRequestStateTest` 一起通过。这个步骤的目的是先固定规则，再改 UI。

- [ ] **Step 3: 在 ParamListUI 中引入刷新状态、短防抖和后台加载**

```java
package io.github.lgp547.anydoorplugin.dialog;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import io.github.lgp547.anydoorplugin.dialog.perf.RefreshRequestState;

public class ParamListUI extends JPanel implements Listener {

    private static final int REFRESH_DEBOUNCE_MS = 120;

    private final RefreshRequestState refreshRequestState = new RefreshRequestState();
    private final Alarm refreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

    private void scheduleRefresh(String qualifiedName, boolean forceNoCache) {
        if (qualifiedName == null) {
            return;
        }
        if (!forceNoCache && !refreshRequestState.shouldSchedule(qualifiedName)) {
            return;
        }

        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(() -> {
            long token = refreshRequestState.markScheduled(qualifiedName);
            DataContext dataContext = DataContext.instance(project);
            if (forceNoCache) {
                dataContext.loadClassDataContextNoCacheAsync(qualifiedName, loaded -> applyLoadedContext(qualifiedName, token, loaded));
            } else {
                dataContext.loadClassDataContextAsync(qualifiedName, loaded -> applyLoadedContext(qualifiedName, token, loaded));
            }
        }, REFRESH_DEBOUNCE_MS);
    }

    private void applyLoadedContext(String qualifiedName, long token, ClassDataContext loaded) {
        if (!refreshRequestState.isLatest(qualifiedName, token)) {
            return;
        }
        context = loaded;
        tableModel.refreshAll(ViewData.toViewData(loaded.data));
    }
}
```

- [ ] **Step 4: 把现有同步刷新入口全部改成走 `scheduleRefresh`**

```java
private void initLoadData() {
    VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
    if (files.length > 0) {
        String qualifiedName = getQualifiedName(files[0]);
        scheduleRefresh(qualifiedName, false);
    }
}
```

```java
private void readAndRefreshTable(VirtualFile file) {
    if (file == null) {
        return;
    }
    String qualifiedName = getQualifiedName(file);
    if (qualifiedName != null) {
        scheduleRefresh(qualifiedName, false);
    }
}
```

```java
toolBar.addToolButton("Refresh", AnyDoorIcons.refresh_icon, AnyDoorIcons.refresh_hover_icon, e -> {
    if (context == null || context.clazz == null) {
        return;
    }
    scheduleRefresh(context.clazz.getQualifiedName(), true);
});
```

```java
@Override
public void onEvent(Event event) {
    if (Objects.equals(event.getType(), EventType.DATA_SYNC) && context != null && context.clazz != null) {
        scheduleRefresh(context.clazz.getQualifiedName(), true);
    }
}
```

- [ ] **Step 5: 在 DataContext 中补上无缓存的异步加载入口，给手动刷新和数据同步使用**

```java
public void loadClassDataContextNoCacheAsync(String qualifiedClassName, Consumer<ClassDataContext> consumer) {
    Objects.requireNonNull(qualifiedClassName);
    Objects.requireNonNull(consumer);

    backgroundExecutor.execute(() -> {
        Data<ParamDataItem> data = dataService.findNoCache(qualifiedClassName);
        PsiClass psiClass = ReadAction.compute(() -> IdeClassUtil.findClass(project, qualifiedClassName));
        ClassDataContext context = new ClassDataContext(psiClass, data, project);
        contextMap.put(qualifiedClassName, context);
        if (!project.isDisposed()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    consumer.accept(context);
                }
            });
        }
    });
}
```

- [ ] **Step 6: 编译参数列表相关代码，确认 UI 重构后仍能通过**

Run: `./gradlew compileJava test --tests "io.github.lgp547.anydoorplugin.dialog.perf.*" -v`  
Workdir: `any-door-plugin`  
Expected: PASS

- [ ] **Step 7: 提交参数列表异步刷新改动**

```bash
git add any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/ParamListUI.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestState.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/RefreshRequestStateAdvancedTest.java
git commit -m "refactor: make param list refresh async"
```

### Task 4: 把搜索弹窗改成防抖和异步过滤

**Files:**
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/ParamListUI.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngine.java`
- Modify: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngineTest.java`

- [ ] **Step 1: 先扩展失败测试，锁定搜索结果顺序和关键字命中逻辑**

```java
@Test
void keepsOriginalInsertionOrderWhenFiltering() {
    ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
    engine.rebuild(List.of(
            sample("A", "demo.S#run1()"),
            sample("B", "demo.S#run2()"),
            sample("C", "demo.S#run3()")
    ));

    List<ParamIndexData> result = engine.search("run", 3);

    assertEquals("A", result.get(0).getName());
    assertEquals("B", result.get(1).getName());
    assertEquals("C", result.get(2).getName());
}
```

- [ ] **Step 2: 运行搜索测试，确认重构前有完整保护**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.ParamIndexSearchEngineTest" -v`  
Workdir: `any-door-plugin`  
Expected: PASS

- [ ] **Step 3: 给 DataContext 增加受限搜索入口，避免 UI 侧直接处理全量索引**

```java
private static final int SEARCH_LIMIT = 200;

public List<ParamIndexData> search(String text) {
    return search(text, SEARCH_LIMIT);
}

public void searchAsync(String text, Consumer<List<ParamIndexData>> consumer) {
    backgroundExecutor.execute(() -> {
        List<ParamIndexData> result = search(text, SEARCH_LIMIT);
        if (!project.isDisposed()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    consumer.accept(result);
                }
            });
        }
    });
}
```

- [ ] **Step 4: 在 ParamListUI 的搜索弹窗中引入防抖和最新请求令牌**

```java
private static final int SEARCH_DEBOUNCE_MS = 180;

private void findAction() {
    SearchTextField searchTextField = new SearchTextField();
    ListTableModel<ViewData> model = new ListTableModel<>(ViewData.SEARCH_COLUMN_NAMES);
    TableView<ViewData> searchTable = new TableView<>(model);
    Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    LatestRequestToken latestSearchToken = new LatestRequestToken();

    searchTextField.addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
            String text = searchTextField.getText();
            searchAlarm.cancelAllRequests();
            searchAlarm.addRequest(() -> {
                long token = latestSearchToken.nextToken();
                DataContext.instance(project).searchAsync(text, result -> {
                    if (!latestSearchToken.isCurrent(token)) {
                        return;
                    }
                    model.setItems(ViewData.toViewData(result));
                });
            }, SEARCH_DEBOUNCE_MS);
        }
    });
}
```

- [ ] **Step 5: 保持搜索引擎实现简单，不在 UI 层重复做 `toLowerCase()`**

```java
private record SearchEntry(ParamIndexData source, String lowerName, String lowerQualifiedMethodName) {
    private SearchEntry(ParamIndexData source) {
        this(
                source,
                normalize(source == null ? null : source.getName()),
                normalize(source == null ? null : source.getQualifiedMethodName())
        );
    }

    private boolean matches(String keyword) {
        return lowerName.contains(keyword) || lowerQualifiedMethodName.contains(keyword);
    }
}
```

- [ ] **Step 6: 跑搜索测试并编译 UI**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.ParamIndexSearchEngineTest" compileJava -v`  
Workdir: `any-door-plugin`  
Expected: PASS

- [ ] **Step 7: 提交搜索异步化和防抖改动**

```bash
git add any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/ParamListUI.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngine.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/ParamIndexSearchEngineTest.java
git commit -m "refactor: debounce and async search popup"
```

### Task 5: 把方法执行弹窗改成异步装载，并完成事件链路收尾

**Files:**
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/MainUI.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/action/AnyDoorPerformed.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/event/DefaultMulticaster.java`
- Modify: `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java`
- Create: `any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestTokenDialogUsageTest.java`

- [ ] **Step 1: 先补一个失败测试，锁定最新请求令牌在弹窗场景也只允许最后一次结果生效**

```java
package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestRequestTokenDialogUsageTest {

    @Test
    void newestDialogLoadWins() {
        LatestRequestToken token = new LatestRequestToken();
        long first = token.nextToken();
        long second = token.nextToken();

        assertFalse(token.isCurrent(first));
        assertTrue(token.isCurrent(second));
    }
}
```

- [ ] **Step 2: 运行令牌测试，确认弹窗异步装载也复用同一套约束**

Run: `./gradlew test --tests "io.github.lgp547.anydoorplugin.dialog.perf.LatestRequestTokenDialogUsageTest" -v`  
Workdir: `any-door-plugin`  
Expected: PASS

- [ ] **Step 3: 改造 MainUI，使其先显示容器，再异步绑定 MethodDataContext**

```java
package io.github.lgp547.anydoorplugin.dialog;

public class MainUI extends DialogWrapper implements Listener {

    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private MethodDataContext context;
    private MainPanel panel;

    public MainUI(String title, Project project) {
        super(project, true, IdeModalityType.MODELESS);
        setTitle(title);
        this.project = project;
        buttonSettings();
        DefaultMulticaster.getInstance(project).addListener(this);
        rootPanel.add(new JLabel("Loading..."), BorderLayout.CENTER);
        init();
    }

    public void bindContext(MethodDataContext context) {
        this.context = context;
        this.panel = new MainPanel(project, context);
        rootPanel.removeAll();
        rootPanel.add(panel, BorderLayout.CENTER);
        rootPanel.revalidate();
        rootPanel.repaint();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return rootPanel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (panel == null || context == null) {
            return null;
        }
        AnyDoorSettingsState settings = project.getService(AnyDoorSettingsState.class);
        return ParamValidationUtil.validate(panel.getEditor().getText(), context.getParamList(),
                settings == null ? null : settings.jsonDateTimeFormat, panel.getEditor());
    }
}
```

- [ ] **Step 4: 改造 AnyDoorPerformed，先创建弹窗，再异步填充上下文**

```java
public void doUseNewUI(AnyDoorSettingsState service, Project project, PsiClass psiClass, PsiMethod psiMethod,
                       String cacheKey, Runnable okAction, @Nullable Long selectedId) {
    ParamCacheDto cache = service.getCache(cacheKey);
    DataContext instance = DataContext.instance(project);
    MainUI mainUI = new MainUI(psiMethod.getName(), project);

    instance.loadExecuteDataContextAsync(
            psiClass.getQualifiedName(),
            IdeClassUtil.getMethodQualifiedName(psiMethod),
            selectedId,
            cache.content(),
            mainUI::bindContext
    );

    mainUI.setOkAction((text) -> {
        okAction.run();
        if (mainUI.isChangePid()) {
            service.pid = mainUI.getPid().longValue();
        }
        ParamCacheDto paramCacheDto = new ParamCacheDto(mainUI.getRunNum(), mainUI.getIsConcurrent(), text);
        service.putCache(cacheKey, paramCacheDto);
        if (psiClass.isInterface()) {
            text = JsonUtil.transformedKey(text, AnyDoorActionUtil.getParamTypeNameTransformer(psiMethod.getParameterList()));
        }
        String jsonDtoStr = getJsonDtoStr(project, psiClass.getQualifiedName(), psiMethod.getName(),
                AnyDoorActionUtil.toParamTypeNameList(psiMethod.getParameterList()), text, !service.enableAsyncExecute, paramCacheDto);
        openAnyDoor(project, jsonDtoStr, service, (url, e) -> NotifierUtil.notifyError(project, "call " + url + " error [ " + e.getMessage() + " ]"));
    });

    mainUI.show();
}
```

- [ ] **Step 5: 把 MainUI 在 DATA_SYNC 时的同步 `context.sync()` 改成后台重载后再 apply，并让 DefaultMulticaster 用监听器快照分发**

```java
@Override
public void onEvent(Event event) {
    if (Objects.equals(EventType.DATA_SYNC, event.getType()) && context != null) {
        DataSyncEvent syncEvent = (DataSyncEvent) event;
        if (Objects.equals(syncEvent.getQualifiedMethodName(), context.getQualifiedMethodName())) {
            DataContext.instance(project).loadExecuteDataContextAsync(
                    context.getClazz().getQualifiedName(),
                    context.getQualifiedMethodName(),
                    context.getSelectedItem() == null ? null : context.getSelectedItem().getId(),
                    context.cacheContent,
                    latest -> {
                        bindContext(latest);
                        latest.fireEvent(EventHelper.createDisplayDataChangeEvent(latest.listDisplayData(), latest.getSelectedItem()));
                    }
            );
        }
    }
}
```

```java
@Override
public void fireEvent(Event event) {
    if (event instanceof DataEvent) {
        if (dataChangeListener != null) {
            dataChangeListener.onEvent(event);
        }
        return;
    }

    List<Listener> listenerSnapshot = List.copyOf(listeners);
    for (Listener listener : listenerSnapshot) {
        listener.onEvent(event);
    }
}
```

- [ ] **Step 6: 跑完整测试和编译，最后做一次插件级验证**

Run: `./gradlew test compileJava buildPlugin -v`  
Workdir: `any-door-plugin`  
Expected: PASS，输出包含 `Task :buildPlugin` 和 `BUILD SUCCESSFUL`

- [ ] **Step 7: 提交方法弹窗异步化和事件链路收尾**

```bash
git add any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/MainUI.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/action/AnyDoorPerformed.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/event/DefaultMulticaster.java \
  any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java \
  any-door-plugin/src/test/java/io/github/lgp547/anydoorplugin/dialog/perf/LatestRequestTokenDialogUsageTest.java
git commit -m "refactor: load main dialog data asynchronously"
```

## Spec Coverage Check

- “类上下文异步加载” 由 Task 2 和 Task 5 覆盖。
- “文件切换驱动的刷新合并” 由 Task 3 覆盖。
- “搜索防抖与标准化索引字段” 由 Task 1 和 Task 4 覆盖。
- “事件触发后的异步跟进” 由 Task 3 和 Task 5 覆盖。
- “prepare/apply 显式拆分” 由 Task 2、Task 3、Task 5 共同覆盖。

## Verification Notes

- 本计划优先对纯逻辑做单元测试，对 IntelliJ UI 侧主要依赖编译和手工回归。
- 如果 `buildPlugin` 过程中暴露 IntelliJ API 兼容问题，优先调整异步 API 的接入方式，不回退到同步刷新。
- 实现时不要修改 JSON 持久化结构，不要在 `ParamIndexData` 中新增会被序列化的缓存字段。
