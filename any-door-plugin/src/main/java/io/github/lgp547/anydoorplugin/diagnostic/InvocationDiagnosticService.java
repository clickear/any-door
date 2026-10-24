package io.github.lgp547.anydoorplugin.diagnostic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import io.github.lgp547.anydoor.common.dto.AnyDoorDiagnosticReport;
import io.github.lgp547.anydoorplugin.AnyDoorInfo;
import io.github.lgp547.anydoorplugin.result.InvocationResultCodec;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.HttpUtil;
import io.github.lgp547.anydoorplugin.util.ImportNewUtil;
import io.github.lgp547.anydoorplugin.util.VmUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class InvocationDiagnosticService {
    private static final int LIMIT = 100;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private final Project project;
    private final Map<String, DiagnosticRecord> records = new LinkedHashMap<>();
    private final List<Consumer<List<DiagnosticRecord>>> listeners = new CopyOnWriteArrayList<>();
    private JsonObject lastRequest;
    private Target lastTarget;
    private long lastAutomaticAt;
    private String lastAutomaticKey;

    public InvocationDiagnosticService(Project project) { this.project = project; }
    public static InvocationDiagnosticService instance(Project project) { return project.getService(InvocationDiagnosticService.class); }

    public synchronized void remember(JsonObject request, AnyDoorSettingsState settings) {
        lastRequest = request.deepCopy(); lastTarget = Target.capture(settings);
    }

    public void diagnoseLast() {
        JsonObject request; Target target;
        synchronized (this) { request = lastRequest == null ? null : lastRequest.deepCopy(); target = lastTarget; }
        if (request == null || target == null) {
            AnyDoorDiagnosticReport report = localFailure("No invocation is available", "Run or open an AnyDoor invocation first");
            addCompleted("No invocation", "IDE", request == null ? new JsonObject() : request, report);
            return;
        }
        diagnose(request, target, null, false);
    }

    public void diagnoseFailure(JsonObject request, AnyDoorSettingsState settings, Throwable invocationFailure) {
        Target target = Target.capture(settings);
        String key = target.description() + ":" + value(request, "className") + "#" + value(request, "methodName") + ":" + invocationFailure.getClass().getName();
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (key.equals(lastAutomaticKey) && now - lastAutomaticAt < 2000) return;
            lastAutomaticKey = key; lastAutomaticAt = now;
        }
        diagnose(request.deepCopy(), target, invocationFailure, true);
    }

    public void retry(DiagnosticRecord record) { diagnose(record.request().deepCopy(), currentTarget(record.target()), null, false); }
    public void retryWithClassLoader(DiagnosticRecord record, String classLoaderId) {
        JsonObject request = record.request().deepCopy(); request.addProperty("classLoaderId", classLoaderId); diagnose(request, currentTarget(record.target()), null, false);
    }
    public void retryWithBean(DiagnosticRecord record, String beanName, String contextId) {
        JsonObject request = record.request().deepCopy(); request.addProperty("beanName", beanName);
        if (contextId != null && !contextId.isBlank() && !"null".equals(contextId)) request.addProperty("beanContextId", contextId);
        diagnose(request, currentTarget(record.target()), null, false);
    }

    public List<DiagnosticRecord> list() { synchronized (records) { ArrayList<DiagnosticRecord> copy = new ArrayList<>(records.values()); Collections.reverse(copy); return copy; } }
    public void clear() { synchronized (records) { records.clear(); } publish(); }
    public AutoCloseable listen(Consumer<List<DiagnosticRecord>> listener) { listeners.add(listener); listener.accept(list()); return () -> listeners.remove(listener); }
    public String toJson(DiagnosticRecord record) { return GSON.toJson(record.report()); }

    private void diagnose(JsonObject request, Target target, Throwable invocationFailure, boolean automatic) {
        prepareDiagnosticRequest(request);
        request.addProperty("diagnosticClientVersion", AnyDoorInfo.ANY_DOOR_JAR_MIN_VERSION);
        String invocation = value(request, "className") + "#" + (request.has("diagnosticMethodName") ? request.get("diagnosticMethodName").getAsString() : "<unknown>");
        String id = UUID.randomUUID().toString();
        DiagnosticRecord pending = new DiagnosticRecord(id, invocation, target.description(), System.currentTimeMillis(), DiagnosticRecord.State.RUNNING, request.deepCopy(), null, null);
        synchronized (records) { records.put(id, pending); trim(); }
        publish(); show();
        AnyDoorDiagnosticReport connection = connectionStage(request, target);
        CompletableFuture<AnyDoorDiagnosticReport> remote = target.attach ? diagnoseAttach(request, target) : diagnoseHttp(request, target);
        remote.orTimeout(20, TimeUnit.SECONDS).whenComplete((report, failure) -> {
            if (failure != null) {
                String message = DiagnosticSanitizer.exception(unwrap(failure), request);
                connection.setSanitizedException(message);
                connection.addStage(failureStage("transport", "Diagnostic transport", message,
                        target.attach ? "Verify the PID, target user, Attach permissions and -XX:+DisableAttachMechanism" : "Verify the HTTP address and that the target uses AnyDoor 2.9.0 or newer"));
                if (invocationFailure != null) addInvocationFailure(connection, invocationFailure, request);
                update(id, current -> current.failure(message, connection));
            } else {
                merge(connection, report);
                if (!AnyDoorDiagnosticReport.PROTOCOL_VERSION.equals(report.getProtocolVersion())) {
                    connection.addStage(failureStage("protocol", "Protocol compatibility", "Unsupported protocol " + report.getProtocolVersion(), "Reload the target with the current AnyDoor Agent"));
                }
                if (invocationFailure != null) addInvocationFailure(connection, invocationFailure, request);
                update(id, current -> current.complete(connection));
            }
        });
    }

    static void prepareDiagnosticRequest(JsonObject request) {
        request.addProperty("operation", "diagnose");
        if (request.has("methodName")) {
            request.add("diagnosticMethodName", request.remove("methodName"));
        }
        if (request.has("parameterTypes")) {
            request.add("diagnosticParameterTypes", request.remove("parameterTypes"));
        }
    }

    private CompletableFuture<AnyDoorDiagnosticReport> diagnoseHttp(JsonObject request, Target target) {
        return HttpUtil.postAsync(target.url + "/any_door/diagnose", request.toString(), 15000)
                .thenApply(InvocationDiagnosticService::decodeReport);
    }

    private CompletableFuture<AnyDoorDiagnosticReport> diagnoseAttach(JsonObject request, Target target) {
        CompletableFuture<AnyDoorDiagnosticReport> result = new CompletableFuture<>();
        Path directory = Path.of(project.getBasePath(), ".idea/any-door/diagnostics");
        Path output = directory.resolve("diagnostic-" + UUID.randomUUID() + ".json");
        Path params = directory.resolve("params-" + UUID.randomUUID() + ".json");
        try {
            Files.createDirectories(directory); request.addProperty("resultPath", output.toString());
            String jar = ImportNewUtil.getPluginLibPath(AnyDoorInfo.ANY_DOOR_ATTACH_JAR);
            CompletableFuture<Void> attach = VmUtil.attachAsync(target.pid, jar, request.toString(), params.toString(),
                    (destination, error) -> result.completeExceptionally(error));
            attach.whenComplete((ignored, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
                else if (!result.isDone()) waitFor(output).whenComplete((envelope, readFailure) -> {
                    if (readFailure != null) result.completeExceptionally(readFailure);
                    else try {
                        InvocationResultCodec.Decoded decoded = InvocationResultCodec.decodeAttach(envelope);
                        if (decoded.error() != null) throw new IllegalStateException(decoded.error());
                        result.complete(decodeReport(decoded.json()));
                    } catch (Throwable error) { result.completeExceptionally(error); }
                });
            });
        } catch (Throwable error) { result.completeExceptionally(error); }
        return result.whenComplete((ignored, failure) -> {
            try { Files.deleteIfExists(params); Files.deleteIfExists(output); } catch (Exception ignoredDelete) { }
        });
    }

    private static CompletableFuture<String> waitFor(Path output) {
        return CompletableFuture.supplyAsync(() -> {
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) try {
                if (Files.exists(output)) return Files.readString(output);
                Thread.sleep(100);
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            catch (Exception error) { throw new IllegalStateException(error); }
            throw new IllegalStateException("Target Agent did not return a diagnostic report within 15 seconds; it may not support diagnostics, reload it with AnyDoor 2.9.0 or newer");
        });
    }

    private AnyDoorDiagnosticReport connectionStage(JsonObject request, Target target) {
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticReport(); report.setInvocationId(UUID.randomUUID().toString()); report.setTarget(target.description());
        AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage("connection", "Target connection"); long started = System.currentTimeMillis();
        if (target.attach) {
            long pid;
            try { pid = Long.parseLong(target.pid); }
            catch (Exception error) { pid = -1; }
            Optional<ProcessHandle> process = pid > 0 ? ProcessHandle.of(pid) : Optional.empty();
            AnyDoorDiagnosticReport.Check check = new AnyDoorDiagnosticReport.Check("connection.process", "Target JVM process", process.isPresent() ? "PASS" : "FAIL",
                    process.isPresent() ? "Process is alive" : "PID does not exist or has exited").evidence("pid=" + target.pid);
            if (process.isPresent()) {
                ProcessHandle.Info info = process.get().info();
                check.evidence("user=" + info.user().orElse("unknown"));
                check.evidence("started=" + info.startInstant().map(Instant::toString).orElse("unknown"));
                check.evidence("command=" + info.command().map(InvocationDiagnosticService::fileName).orElse("unknown"));
            } else check.suggest("Select a currently running JVM process");
            stage.add(check);
        } else stage.add(new AnyDoorDiagnosticReport.Check("connection.http", "HTTP endpoint", "PASS", "Address prepared; reachability is checked by the diagnostic request").evidence("url=" + target.url));
        stage.setDurationMillis(System.currentTimeMillis() - started); report.addStage(stage); return report;
    }

    private static void merge(AnyDoorDiagnosticReport destination, AnyDoorDiagnosticReport remote) { for (AnyDoorDiagnosticReport.Stage stage : remote.getStages()) destination.addStage(stage); }
    private static void addInvocationFailure(AnyDoorDiagnosticReport report, Throwable failure, JsonObject request) { String safe = DiagnosticSanitizer.exception(failure, request); report.setSanitizedException(safe); report.addStage(failureStage("invocation", "Original invocation failure", safe, "Use the failed diagnostic checks above to correct the invocation and retry")); }
    private static AnyDoorDiagnosticReport.Stage failureStage(String id, String name, String message, String suggestion) { AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage(id, name); stage.add(new AnyDoorDiagnosticReport.Check(id + ".failure", name, "FAIL", firstLine(message)).suggest(suggestion)); return stage; }
    private static AnyDoorDiagnosticReport localFailure(String message, String suggestion) { AnyDoorDiagnosticReport report = new AnyDoorDiagnosticReport(); report.setTarget("IDE"); report.addStage(failureStage("request", "Invocation request", message, suggestion)); return report; }
    private void addCompleted(String invocation, String target, JsonObject request, AnyDoorDiagnosticReport report) { String id=UUID.randomUUID().toString(); synchronized(records){records.put(id,new DiagnosticRecord(id,invocation,target,System.currentTimeMillis(),DiagnosticRecord.State.FAIL,request,report,null));trim();}publish();show(); }
    private void update(String id, java.util.function.UnaryOperator<DiagnosticRecord> operation) { synchronized(records){DiagnosticRecord value=records.get(id);if(value!=null)records.put(id,operation.apply(value));}publish(); }
    private void trim() { while(records.size()>LIMIT)records.remove(records.keySet().iterator().next()); }
    private void publish() { List<DiagnosticRecord> snapshot=list(); listeners.forEach(listener->listener.accept(snapshot)); }
    private void show() { ApplicationManager.getApplication().invokeLater(() -> { if(project.isDisposed())return;var window=ToolWindowManager.getInstance(project).getToolWindow("Any-Door");if(window==null)return;window.show(()->{var content=window.getContentManager().findContent("Diagnostics");if(content!=null)window.getContentManager().setSelectedContent(content);});}); }
    private Target currentTarget(String description) { AnyDoorSettingsState settings=project.getService(AnyDoorSettingsState.class);return Target.capture(settings); }
    private static String value(JsonObject request,String key){return request.has(key)?request.get(key).getAsString():"<unknown>";}
    private static String firstLine(String value){if(value==null)return "Unknown error";int end=value.indexOf('\n');return end<0?value:value.substring(0,end);}
    private static Throwable unwrap(Throwable error){while((error instanceof java.util.concurrent.CompletionException||error instanceof java.util.concurrent.ExecutionException)&&error.getCause()!=null)error=error.getCause();return error;}
    private static String fileName(String path){try{return Path.of(path).getFileName().toString();}catch(Exception ignored){return "java";}}
    static AnyDoorDiagnosticReport decodeReport(String json) {
        JsonObject root;
        try { root = JsonParser.parseString(json).getAsJsonObject(); }
        catch (Throwable error) { throw new IllegalStateException("Target returned an invalid diagnostic response", error); }
        if (!root.has("protocolVersion") || !root.has("stages")) throw new IllegalStateException("Target Agent does not support the diagnostic protocol; reload it with AnyDoor 2.9.0 or newer");
        return GSON.fromJson(root, AnyDoorDiagnosticReport.class);
    }

    private record Target(boolean attach,String pid,String url){
        static Target capture(AnyDoorSettingsState settings){return new Target(settings.isSelectJavaAttach(),String.valueOf(settings.pid),settings.mvcAddress+":"+settings.mvcPort+settings.mvcWebPathPrefix);}
        String description(){return attach?"PID "+pid:url;}
    }
}
