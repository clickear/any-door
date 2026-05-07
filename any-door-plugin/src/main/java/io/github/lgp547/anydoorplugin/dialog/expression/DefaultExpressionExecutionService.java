package io.github.lgp547.anydoorplugin.dialog.expression;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import io.github.lgp547.anydoorplugin.AnyDoorInfo;
import io.github.lgp547.anydoorplugin.dialog.MethodDataContext;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.AnyDoorActionUtil;
import io.github.lgp547.anydoorplugin.util.ImportNewUtil;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import io.github.lgp547.anydoorplugin.util.VmUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultExpressionExecutionService implements ExpressionExecutionService {

    private static final long WAIT_TIMEOUT_SECONDS = 15;

    @Override
    public ExpressionResult execute(Project project, MethodDataContext context, ExpressionRequest request) {
        AnyDoorSettingsState settings = AnyDoorSettingsState.getAnyDoorSettingsState(project);
        if (!settings.isSelectJavaAttach()) {
            throw new UnsupportedOperationException("Java expression mode currently supports Java Attach only");
        }
        PsiMethod method = context.getMethod();
        PsiClass psiClass = context.getClazz();
        if (method == null || psiClass == null || psiClass.getQualifiedName() == null) {
            throw new IllegalStateException("Method context is unavailable");
        }

        File resultFile = new File(project.getBasePath(), ".idea/any-door/AnyDoorExpressionResult.json");
        deleteIfExists(resultFile);
        String jsonDtoStr = buildExpressionJson(project, settings, psiClass, method, request, resultFile);

        String anyDoorJarPath = ImportNewUtil.getPluginLibPath(AnyDoorInfo.ANY_DOOR_ATTACH_JAR);
        String paramPath = project.getBasePath() + "/.idea/any-door/AnyDoorExpressionParam.json";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<Exception>();
        VmUtil.attachAsync(String.valueOf(settings.pid), anyDoorJarPath, jsonDtoStr, paramPath, (target, error) -> {
            errorRef.set(error);
            latch.countDown();
        });

        String resultJson = waitForResult(resultFile, latch, errorRef);
        return new ExpressionResult(resultJson, unwrapResult(resultJson));
    }

    private static String waitForResult(File resultFile, CountDownLatch latch, AtomicReference<Exception> errorRef) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_TIMEOUT_SECONDS);
        while (System.currentTimeMillis() < deadline) {
            Exception error = errorRef.get();
            if (error != null) {
                throw new IllegalStateException("Expression attach failed", error);
            }
            if (resultFile.isFile()) {
                try {
                    return new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to read expression result", e);
                }
            }
            try {
                if (latch.await(150, TimeUnit.MILLISECONDS) && errorRef.get() != null) {
                    throw new IllegalStateException("Expression attach failed", errorRef.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting expression result", e);
            }
        }
        throw new IllegalStateException("Timed out waiting for expression result");
    }

    private static String unwrapResult(String resultJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JsonUtil.objectMapper.readTree(resultJson);
            if (!node.isObject() || !node.has("success")) {
                throw new IllegalStateException("Invalid expression result payload");
            }
            if (!node.path("success").asBoolean(false)) {
                throw new IllegalStateException(node.path("error").asText("Unknown expression error"));
            }
            return node.path("value").toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid expression result payload", e);
        }
    }

    private static String buildExpressionJson(Project project,
                                              AnyDoorSettingsState settings,
                                              PsiClass psiClass,
                                              PsiMethod psiMethod,
                                              ExpressionRequest request,
                                              File resultFile) {
        JsonObject jsonObjectReq = new JsonObject();
        jsonObjectReq.addProperty("content", JsonUtil.compressJson(request.fullJsonText()));
        jsonObjectReq.addProperty("methodName", psiMethod.getName());
        jsonObjectReq.addProperty("className", psiClass.getQualifiedName());
        jsonObjectReq.addProperty("sync", true);
        jsonObjectReq.add("parameterTypes", JsonUtil.toJsonArray(AnyDoorActionUtil.toParamTypeNameList(psiMethod.getParameterList())));

        jsonObjectReq.add("jarPaths", JsonUtil.toJsonArray(ImportNewUtil.getAnyDoorRuntimeJarPaths()));
        jsonObjectReq.addProperty("projectBasePath", project.getBasePath());
        jsonObjectReq.addProperty("jsonTimezone", settings.jsonTimezone);
        jsonObjectReq.addProperty("jsonDateTimeFormat", settings.jsonDateTimeFormat);
        jsonObjectReq.addProperty("expressionMode", true);
        jsonObjectReq.addProperty("expression", normalizeExpression(request.expression()));
        jsonObjectReq.addProperty("expressionResultPath", resultFile.getAbsolutePath());
        jsonObjectReq.addProperty("expressionRootParamName", request.target().rootParamName());
        jsonObjectReq.addProperty("expressionJsonPath", request.target().jsonPath());
        return jsonObjectReq.toString();
    }

    private static String normalizeExpression(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Failed to reset expression result file");
        }
    }
}
