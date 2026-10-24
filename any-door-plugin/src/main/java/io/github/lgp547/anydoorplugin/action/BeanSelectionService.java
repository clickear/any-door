package io.github.lgp547.anydoorplugin.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.github.lgp547.anydoorplugin.AnyDoorInfo;
import io.github.lgp547.anydoorplugin.dialog.BeanSelectionDialog;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.result.InvocationResultCodec;
import io.github.lgp547.anydoorplugin.result.InvocationResultService;
import io.github.lgp547.anydoorplugin.util.HttpUtil;
import io.github.lgp547.anydoorplugin.util.ImportNewUtil;
import io.github.lgp547.anydoorplugin.util.VmUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** Discovers beans off the UI thread before dispatching through the same captured target. */
public final class BeanSelectionService {
    private BeanSelectionService() { }

    public static void execute(Project project, String dto, AnyDoorSettingsState settings,
                               BiConsumer<String, Exception> errors) {
        execute(project, dto, settings, errors, () -> { });
    }

    public static void execute(Project project, String dto, AnyDoorSettingsState settings,
                               BiConsumer<String, Exception> errors, Runnable onDispatch) {
        Target target = new Target(settings.isSelectJavaAttach(), String.valueOf(settings.pid),
                settings.mvcAddress + ":" + settings.mvcPort + settings.mvcWebPathPrefix + "/any_door/run");
        JsonObject request = JsonParser.parseString(dto).getAsJsonObject();
        if (request.has("beanContextId") && !request.has("beanName")) {
            errors.accept(target.description(), new IllegalArgumentException("指定 beanContextId 时必须同时指定 beanName"));
            return;
        }
        if (request.has("beanName") && !request.get("beanName").getAsString().isBlank()) {
            send(project, target, request, errors, onDispatch);
            return;
        }
        JsonObject query = request.deepCopy();
        query.addProperty("operation", "listBeans");
        query.remove("content");
        // Older agents must reject discovery, never mistake it for a business invocation.
        query.remove("methodName");
        query.remove("parameterTypes");
        query.remove("beanContextId");
        discover(project, target, query).whenComplete((candidates, failure) -> {
            if (failure != null) {
                errors.accept(target.description(), new IllegalStateException("查询 Spring Bean 失败：" + failure.getMessage(), failure));
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) return;
                JsonObject selected = null;
                if (candidates.size() == 1) selected = candidates.get(0);
                if (candidates.size() > 1) {
                    BeanSelectionDialog dialog = new BeanSelectionDialog(project, request.get("className").getAsString(), candidates);
                    if (!dialog.showAndGet()) return;
                    selected = dialog.getSelectedBean();
                }
                if (selected != null) {
                    request.addProperty("beanName", selected.get("beanName").getAsString());
                    request.addProperty("beanContextId", selected.get("beanContextId").getAsString());
                    if (selected.has("classLoaderId")) request.addProperty("classLoaderId", selected.get("classLoaderId").getAsString());
                }
                send(project, target, request, errors, onDispatch);
            });
        });
    }

    private static CompletableFuture<List<JsonObject>> discover(Project project, Target target, JsonObject query) {
        if (!target.attach) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return parseCandidates(HttpUtil.postByJdk(target.url, query.toString()));
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
        }
        CompletableFuture<List<JsonObject>> result = new CompletableFuture<>();
        Path resultFile = Path.of(project.getBasePath(), ".idea/any-door/results/beans-" + UUID.randomUUID() + ".json");
        query.addProperty("resultPath", resultFile.toString());
        try {
            Files.createDirectories(resultFile.getParent());
            attach(project, target, query, (destination, error) -> result.completeExceptionally(error))
                    .whenComplete((ignored, failure) -> {
                        try {
                            if (failure != null) throw new IllegalStateException(failure);
                            if (result.isDone()) return;
                            JsonObject response = JsonParser.parseString(Files.readString(resultFile)).getAsJsonObject();
                            if (!"success".equals(response.get("status").getAsString())) {
                                throw new IllegalStateException(response.get("result").getAsString());
                            }
                            result.complete(parseCandidates(response.get("result").getAsString()));
                        } catch (Exception e) { result.completeExceptionally(e); }
                        finally {
                            try { Files.deleteIfExists(resultFile); } catch (Exception ignoredError) { }
                        }
                    });
        } catch (Exception e) { result.completeExceptionally(e); }
        return result.orTimeout(10, TimeUnit.SECONDS);
    }

    static List<JsonObject> parseCandidates(String json) {
        List<JsonObject> result = new ArrayList<>();
        for (var element : JsonParser.parseString(json).getAsJsonArray()) result.add(element.getAsJsonObject());
        return result;
    }

    private static void send(Project project, Target target, JsonObject request,
                             BiConsumer<String, Exception> errors, Runnable onDispatch) {
        String invocation = request.get("className").getAsString() + "#" + request.get("methodName").getAsString();
        String resultId = InvocationResultService.instance(project).begin(invocation, target.description());
        if (!target.attach) {
            onDispatch.run();
            HttpUtil.postAsync(target.url + "-result", request.toString()).whenComplete((response, failure) -> {
                if (failure != null) {
                    Exception error = unwrap(failure);
                    InvocationResultService.instance(project).failure(resultId, error);
                    errors.accept(target.description(), error);
                } else InvocationResultService.instance(project).success(resultId, InvocationResultCodec.prettyResponse(response));
            });
            return;
        }
        if (request.has("resultPath")) {
            onDispatch.run();
            Path result = Path.of(request.get("resultPath").getAsString());
            attach(project, target, request, errors).thenCompose(ignored -> readWhenReady(result))
                    .whenComplete((envelope, failure) -> completeAttach(project, resultId, envelope, failure, errors, target));
            return;
        }
        // Dialog calls also need an acknowledgement: loadAgent can return normally
        // even when the agent reports that a selected bean has disappeared.
        Path acknowledgement = Path.of(project.getBasePath(), ".idea/any-door/results/call-" + UUID.randomUUID() + ".json");
        try {
            Files.createDirectories(acknowledgement.getParent());
            request.addProperty("resultPath", acknowledgement.toString());
            onDispatch.run();
            attach(project, target, request, errors).thenCompose(ignored -> readWhenReady(acknowledgement)).whenComplete((envelope, failure) -> {
                try {
                    if (failure != null) throw new IllegalStateException(failure);
                    completeEnvelope(project, resultId, envelope, errors, target);
                } catch (Exception e) { InvocationResultService.instance(project).failure(resultId, e); errors.accept(target.description(), e); }
                finally {
                    try { Files.deleteIfExists(acknowledgement); } catch (Exception ignoredError) { }
                }
            });
        } catch (Exception e) { InvocationResultService.instance(project).failure(resultId, e); errors.accept(target.description(), e); }
    }

    private static void completeAttach(Project project, String resultId, String envelope, Throwable failure,
                                       BiConsumer<String, Exception> errors, Target target) {
        try {
            if (failure != null) throw new IllegalStateException(failure);
            completeEnvelope(project, resultId, envelope, errors, target);
        } catch (Exception e) {
            InvocationResultService.instance(project).failure(resultId, e); errors.accept(target.description(), e);
        }
    }

    private static CompletableFuture<String> readWhenReady(Path result) {
        return CompletableFuture.supplyAsync(() -> {
            long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
            while (System.currentTimeMillis() < deadline) {
                try {
                    if (Files.exists(result)) return Files.readString(result);
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting for invocation result", interrupted);
                } catch (Exception error) { throw new IllegalStateException("Unable to read invocation result", error); }
            }
            throw new IllegalStateException("Invocation result timed out after 5 minutes");
        });
    }

    private static void completeEnvelope(Project project, String resultId, String envelope,
                                         BiConsumer<String, Exception> errors, Target target) {
        InvocationResultCodec.Decoded decoded = InvocationResultCodec.decodeAttach(envelope);
        if (decoded.error() == null) InvocationResultService.instance(project).success(resultId, decoded.json());
        else {
            IllegalStateException error = new IllegalStateException(decoded.error());
            InvocationResultService.instance(project).failure(resultId, error); errors.accept(target.description(), error);
        }
    }

    private static Exception unwrap(Throwable failure) {
        Throwable value = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return value instanceof Exception ? (Exception) value : new IllegalStateException(value);
    }

    private static CompletableFuture<Void> attach(Project project, Target target, JsonObject request,
                                                   BiConsumer<String, Exception> errors) {
        String jar = ImportNewUtil.getPluginLibPath(AnyDoorInfo.ANY_DOOR_ATTACH_JAR);
        Path params = Path.of(project.getBasePath(), ".idea/any-door/params-" + UUID.randomUUID() + ".json");
        return VmUtil.attachAsync(target.pid, jar, request.toString(), params.toString(), errors)
                .whenComplete((ignored, failure) -> {
                    try { Files.deleteIfExists(params); } catch (Exception ignoredError) { }
                });
    }

    private record Target(boolean attach, String pid, String url) {
        String description() { return attach ? "PID " + pid : url; }
    }
}
