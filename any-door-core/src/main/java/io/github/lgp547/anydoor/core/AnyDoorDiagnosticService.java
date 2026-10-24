package io.github.lgp547.anydoor.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lgp547.anydoor.common.dto.AnyDoorDiagnosticReport;
import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorClassUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorSpringUtil;
import io.github.lgp547.anydoor.util.JsonUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only preflight. It never resolves/creates a bean and never invokes the business method. */
public final class AnyDoorDiagnosticService {
    public AnyDoorDiagnosticReport diagnose(String requestJson, List<Class<?>> types,
                                             List<Map<String, String>> beans, String target,
                                             String classFailure, String springFailure) {
        AnyDoorRunDto request = JsonUtil.toJavaBean(requestJson, AnyDoorRunDto.class);
        Throwable failure = classFailure == null ? null : new IllegalArgumentException(classFailure);
        Throwable springError = springFailure == null ? null : new IllegalArgumentException(springFailure);
        return diagnose(request, types, beans, target, failure, springError);
    }

    public AnyDoorDiagnosticReport diagnose(AnyDoorRunDto request) {
        normalizeDiagnosticSignature(request);
        List<Class<?>> types = new ArrayList<>();
        Throwable classFailure = null;
        try { types.add(AnyDoorClassUtil.forName(request.getClassName())); }
        catch (Throwable error) { classFailure = error; }
        List<Map<String, String>> beans = Collections.emptyList();
        Throwable springFailure = null;
        if (!types.isEmpty()) {
            try { beans = AnyDoorSpringUtil.listBeans(types.get(0)); }
            catch (Throwable error) { springFailure = error; }
        }
        return diagnose(request, types, beans, "HTTP", classFailure, springFailure);
    }

    public AnyDoorDiagnosticReport diagnose(AnyDoorRunDto request, List<Class<?>> types,
                                             List<Map<String, String>> beans, String target,
                                             Throwable classFailure) {
        return diagnose(request, types, beans, target, classFailure, null);
    }

    public AnyDoorDiagnosticReport diagnose(AnyDoorRunDto request, List<Class<?>> types,
                                             List<Map<String, String>> beans, String target,
                                             Throwable classFailure, Throwable springFailure) {
        normalizeDiagnosticSignature(request);
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticReport();
        report.setInvocationId(UUID.randomUUID().toString());
        report.setTarget(target);
        report.addStage(agentStage(request));
        report.addStage(classLoaderStage(request, types, classFailure));
        report.addStage(springStage(request, beans, springFailure));
        Method method = null;
        AnyDoorDiagnosticReport.Stage methodStage = new AnyDoorDiagnosticReport.Stage("method", "Method resolution");
        long started = System.currentTimeMillis();
        if (types.isEmpty()) {
            methodStage.add(check("method.skipped", "Method lookup", "SKIP", "Target class was not available"));
        } else {
            List<Method> candidates = new ArrayList<>();
            Throwable metadataFailure = null;
            try { candidates = methods(types, request.getMethodName()); }
            catch (Throwable error) { metadataFailure = error; }
            if (metadataFailure != null) methodStage.add(check("method.metadata", "Method metadata", "FAIL", safe(metadataFailure))
                    .suggest("A method signature dependency is missing from the selected application ClassLoader"));
            AnyDoorDiagnosticReport.Check candidatesCheck = check("method.candidates", "Method candidates",
                    candidates.isEmpty() ? "FAIL" : "PASS", candidates.isEmpty() ? "No matching method name" : candidates.size() + " candidate(s)");
            for (Method candidate : candidates) candidatesCheck.evidence(signature(candidate));
            if (candidates.isEmpty()) candidatesCheck.suggest("Verify the source method name and redeploy the target application");
            methodStage.add(candidatesCheck);
            try {
                method = AnyDoorClassUtil.getMethod(types.get(0), request.getMethodName(), request.getParameterTypes());
                methodStage.add(check("method.selected", "Resolved method", "PASS", signature(method))
                        .evidence("declaringClass=" + method.getDeclaringClass().getName())
                        .evidence("modifiers=" + Modifier.toString(method.getModifiers()))
                        .evidence("bridge=" + method.isBridge() + ", synthetic=" + method.isSynthetic())
                        .evidence("abstract=" + Modifier.isAbstract(method.getModifiers())));
            } catch (Throwable error) {
                methodStage.add(check("method.selected", "Resolved method", "FAIL", safe(error))
                        .suggest(candidates.size() > 1 ? "Specify the exact parameterTypes for the overload" : "Verify the deployed method signature"));
            }
        }
        methodStage.setDurationMillis(System.currentTimeMillis() - started);
        report.addStage(methodStage);
        report.addStage(parameterStage(request, method));
        report.addStage(executionStage(request));
        return report;
    }

    private static AnyDoorDiagnosticReport.Stage agentStage(AnyDoorRunDto request) {
        long started = System.currentTimeMillis();
        AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage("agent", "Agent and protocol");
        stage.add(check("agent.protocol", "Diagnostic protocol", "PASS", AnyDoorDiagnosticReport.PROTOCOL_VERSION)
                .evidence("runtimeJava=" + System.getProperty("java.version"))
                .evidence("vm=" + System.getProperty("java.vm.name")));
        stage.add(classAvailable("agent.jackson", "Jackson dependency", "com.fasterxml.jackson.databind.ObjectMapper"));
        stage.add(classAvailable("agent.spring", "Spring dependency", "org.springframework.context.ApplicationContext"));
        stage.add(component("agent.core", "Core component", AnyDoorDiagnosticService.class));
        stage.add(component("agent.common", "Common component", AnyDoorRunDto.class));
        String expected = request.getDiagnosticClientVersion();
        String coreSource = sourceName(AnyDoorDiagnosticService.class), commonSource = sourceName(AnyDoorRunDto.class);
        boolean compatible = expected == null || expected.isEmpty() || (coreSource.contains(expected) && commonSource.contains(expected));
        stage.add(check("agent.compatibility", "Plugin and runtime compatibility", compatible ? "PASS" : "WARN",
                expected == null ? "Client version was not supplied" : "client=" + expected)
                .evidence("core=" + coreSource).evidence("common=" + commonSource)
                .suggest(compatible ? "No action required" : "Reload the target with the Agent bundled in the current plugin"));
        java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
        stage.add(check("agent.runtime", "Target JVM", "PASS", System.getProperty("java.version"))
                .evidence("user=" + System.getProperty("user.name", "unknown"))
                .evidence("started=" + new java.util.Date(runtime.getStartTime()).toString())
                .evidence("uptimeMillis=" + runtime.getUptime()));
        stage.setDurationMillis(System.currentTimeMillis() - started);
        return stage;
    }

    private static AnyDoorDiagnosticReport.Stage classLoaderStage(AnyDoorRunDto request, List<Class<?>> types, Throwable failure) {
        long started = System.currentTimeMillis();
        AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage("classloader", "Class and classloader");
        if (types.isEmpty()) {
            stage.add(check("class.loaded", "Target class", "FAIL", failure == null ? "Class was not found" : safe(failure))
                    .evidence("className=" + request.getClassName())
                    .evidence("requestedClassLoaderId=" + nullSafe(request.getClassLoaderId()))
                    .suggest("Confirm the deployed class name and select the application ClassLoader"));
        } else {
            AnyDoorDiagnosticReport.Check loaded = check("class.loaded", "Target class", types.size() > 1 ? "WARN" : "PASS",
                    types.size() + " matching class definition(s)");
            for (Class<?> type : types) loaded.evidence(loaderEvidence(type));
            if (types.size() > 1 && empty(request.getClassLoaderId())) loaded.suggest("Select one classLoaderId before invoking the method");
            stage.add(loaded);
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            boolean visible = false;
            try { visible = Class.forName(request.getClassName(), false, tccl) == types.get(0); } catch (Throwable ignored) { }
            stage.add(check("class.tccl", "Thread context ClassLoader", visible ? "PASS" : "WARN",
                    visible ? "TCCL resolves the selected class" : "TCCL resolves another class or cannot resolve it")
                    .evidence("tccl=" + loaderName(tccl))
                    .suggest(visible ? "No action required" : "AnyDoor will switch TCCL to the selected application loader during invocation"));
        }
        stage.setDurationMillis(System.currentTimeMillis() - started);
        return stage;
    }

    private static AnyDoorDiagnosticReport.Stage springStage(AnyDoorRunDto request, List<Map<String, String>> beans, Throwable failure) {
        long started = System.currentTimeMillis();
        AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage("spring", "Spring environment");
        Set<String> contexts = new LinkedHashSet<>();
        for (Map<String, String> bean : beans) if (bean.get("beanContextId") != null) contexts.add(bean.get("beanContextId"));
        AnyDoorDiagnosticReport.Check found = check("spring.beans", "Bean candidates", failure != null ? "FAIL" : beans.size() > 1 && empty(request.getBeanName()) ? "WARN" : "PASS",
                failure != null ? safe(failure) : beans.isEmpty() ? "No Spring Bean candidate; invocation would use a plain instance" : beans.size() + " Bean candidate(s) in " + contexts.size() + " context(s)");
        for (Map<String, String> bean : beans) found.evidence("name=" + bean.get("beanName") + ", context=" + bean.get("contextName")
                + ", contextId=" + bean.get("beanContextId") + ", type=" + bean.get("className") + ", proxy=" + bean.get("proxyType") + ", loader=" + bean.get("classLoaderId"));
        if (beans.size() > 1 && empty(request.getBeanName())) found.suggest("Select the intended Bean and ApplicationContext before invocation");
        if (failure != null) found.suggest("Verify Spring framework visibility in the selected ClassLoader and that the ApplicationContext is active");
        stage.add(found);
        if (!empty(request.getBeanName())) {
            boolean selected = false;
            for (Map<String, String> bean : beans) if (request.getBeanName().equals(bean.get("beanName"))
                    && (empty(request.getBeanContextId()) || request.getBeanContextId().equals(bean.get("beanContextId")))) selected = true;
            stage.add(check("spring.selection", "Selected Bean", selected ? "PASS" : "FAIL",
                    selected ? request.getBeanName() : "Selected Bean is missing or stale")
                    .suggest(selected ? "No action required" : "Refresh Bean candidates and select an active Bean"));
        }
        stage.setDurationMillis(System.currentTimeMillis() - started);
        return stage;
    }

    private static AnyDoorDiagnosticReport.Stage parameterStage(AnyDoorRunDto request, Method method) {
        long started = System.currentTimeMillis();
        AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage("parameters", "Parameter binding");
        if (method == null) stage.add(check("parameters.skipped", "Parameter validation", "SKIP", "Method was not resolved"));
        else {
            String content = JsonUtil.toStrNotExc(request.getContent());
            JsonNode body = null;
            try { body = JsonUtil.objectMapper.readTree(content); }
            catch (Throwable error) { stage.add(check("parameters.json", "JSON syntax", "FAIL", safe(error)).suggest("Correct the JSON body before invoking")); }
            if (body != null) {
                stage.add(check("parameters.json", "JSON syntax", "PASS", body.isArray() ? "Batch array" : body.getNodeType().name()));
                Parameter[] parameters = method.getParameters();
                boolean names = true;
                for (Parameter parameter : parameters) names &= parameter.isNamePresent();
                String binding = method.getDeclaringClass().isInterface() || !names ? "argsN positional binding" : "Java parameter-name binding";
                stage.add(check("parameters.binding", "Binding strategy", names ? "PASS" : "WARN", binding)
                        .evidence("parameterCount=" + parameters.length)
                        .suggest(names ? "No action required" : "Use args0, args1 ... keys or compile the target with -parameters"));
                JsonNode sample = body.isArray() && body.size() > 0 ? body.get(0) : body;
                for (int i = 0; i < parameters.length; i++) {
                    String key = names && !method.getDeclaringClass().isInterface() ? parameters[i].getName() : "args" + i;
                    JsonNode value = sample == null ? null : sample.get(key);
                    String status = value == null ? (parameters[i].getType().isPrimitive() ? "FAIL" : "WARN") : compatible(value, parameters[i].getType()) ? "PASS" : "FAIL";
                    stage.add(check("parameters." + i, "Parameter " + i, status,
                            key + " -> " + parameters[i].getParameterizedType().getTypeName())
                            .evidence("jsonPath=$." + key)
                            .evidence("sourceType=" + (value == null ? "missing/null" : value.getNodeType().name()))
                            .suggest("FAIL".equals(status) ? "Correct the value at $." + key + " to match " + parameters[i].getType().getTypeName() : "Conversion is finalized only during the real invocation"));
                }
            }
        }
        stage.setDurationMillis(System.currentTimeMillis() - started);
        return stage;
    }

    private static AnyDoorDiagnosticReport.Stage executionStage(AnyDoorRunDto request) {
        AnyDoorDiagnosticReport.Stage stage = new AnyDoorDiagnosticReport.Stage("execution", "Execution and result");
        stage.add(check("execution.safe", "Business method", "SKIP", "Not executed during diagnostics")
                .evidence("mode=" + (Boolean.TRUE.equals(request.getSync()) ? "sync" : "async"))
                .evidence("count=" + request.getNum() + ", concurrent=" + Boolean.TRUE.equals(request.getConcurrent())));
        return stage;
    }

    private static List<Method> methods(List<Class<?>> types, String name) {
        Map<String, Method> result = new LinkedHashMap<>();
        if (name == null) return new ArrayList<>();
        for (Class<?> type : types) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) if (name.equals(method.getName())) result.put(signature(method), method);
            }
            for (Method method : type.getMethods()) if (name.equals(method.getName())) result.put(signature(method), method);
        }
        return new ArrayList<>(result.values());
    }

    private static boolean compatible(JsonNode value, Class<?> type) {
        if (value == null || value.isNull()) return !type.isPrimitive();
        if (type == boolean.class || type == Boolean.class) return value.isBoolean();
        if (type.isPrimitive() || Number.class.isAssignableFrom(type)) return value.isNumber();
        if (type == String.class || type == char.class || type == Character.class || type.isEnum()) return value.isTextual();
        if (java.time.temporal.Temporal.class.isAssignableFrom(type)) return value.isTextual();
        if (java.util.Date.class.isAssignableFrom(type)) return value.isTextual() || value.isNumber();
        if (Map.class.isAssignableFrom(type)) return value.isObject();
        if (type.isArray() || Iterable.class.isAssignableFrom(type)) return value.isArray();
        return value.isObject();
    }

    private static AnyDoorDiagnosticReport.Check classAvailable(String id, String title, String name) {
        try { Class.forName(name, false, AnyDoorDiagnosticService.class.getClassLoader()); return check(id, title, "PASS", name); }
        catch (Throwable error) { return check(id, title, "FAIL", safe(error)).suggest("Rebuild the isolated Agent dependency bundle"); }
    }
    private static AnyDoorDiagnosticReport.Check component(String id, String title, Class<?> type) {
        String version = type.getPackage() == null ? null : type.getPackage().getImplementationVersion();
        String source = sourceName(type);
        return check(id, title, "PASS", version == null ? source : version).evidence("artifact=" + source).evidence("implementationVersion=" + (version == null ? "not declared" : version));
    }
    private static String sourceName(Class<?> type) { try { return new java.io.File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getName(); } catch (Throwable ignored) { return "unknown"; } }
    private static void normalizeDiagnosticSignature(AnyDoorRunDto request) {
        if (request.isDiagnose() && request.getMethodName() == null) {
            request.setMethodName(request.getDiagnosticMethodName());
            request.setParameterTypes(request.getDiagnosticParameterTypes());
        }
    }
    private static AnyDoorDiagnosticReport.Check check(String id, String title, String status, String summary) { return new AnyDoorDiagnosticReport.Check(id, title, status, summary); }
    private static String signature(Method method) { StringBuilder value = new StringBuilder(method.getName()).append('('); Class<?>[] types = method.getParameterTypes(); for (int i=0;i<types.length;i++){if(i>0)value.append(',');value.append(types[i].getTypeName());} return value.append(") -> ").append(method.getReturnType().getTypeName()).toString(); }
    private static String loaderEvidence(Class<?> type) { ClassLoader loader = type.getClassLoader(); return "class=" + type.getName() + ", loader=" + loaderName(loader) + ", id=" + AnyDoorClassUtil.classLoaderId(loader) + ", parent=" + loaderName(loader == null ? null : loader.getParent()); }
    private static String loaderName(ClassLoader loader) { return loader == null ? "bootstrap" : loader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(loader)); }
    private static boolean empty(String value) { return value == null || value.isEmpty(); }
    private static String nullSafe(String value) { return value == null ? "<none>" : value; }
    private static String safe(Throwable error) { String value = error.getClass().getName() + ": " + String.valueOf(error.getMessage()); return value.length() > 1000 ? value.substring(0, 1000) + "…" : value; }
}
