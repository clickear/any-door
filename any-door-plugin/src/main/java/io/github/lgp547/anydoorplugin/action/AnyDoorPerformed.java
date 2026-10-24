package io.github.lgp547.anydoorplugin.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.openapi.application.ReadAction;
import io.github.lgp547.anydoorplugin.AnyDoorInfo;
import io.github.lgp547.anydoorplugin.dialog.DataContext;
import io.github.lgp547.anydoorplugin.dialog.MainUI;
import io.github.lgp547.anydoorplugin.dialog.old.TextAreaDialog;
import io.github.lgp547.anydoorplugin.dto.ParamCacheDto;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import io.github.lgp547.anydoorplugin.util.AnyDoorActionUtil;
import io.github.lgp547.anydoorplugin.util.ImportNewUtil;
import io.github.lgp547.anydoorplugin.util.JsonUtil;
import io.github.lgp547.anydoorplugin.util.NotifierUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * 打开任意门，核心执行
 */
public class AnyDoorPerformed {

    public void invoke(Project project, PsiMethod psiMethod, Runnable okAction) {
        MethodInvocationSnapshot method = MethodInvocationSnapshot.capture(psiMethod);


        Optional<AnyDoorSettingsState> anyDoorSettingsStateOpt = AnyDoorSettingsState.getAnyDoorSettingsStateNotExc(project);
        if (anyDoorSettingsStateOpt.isEmpty()) {
            return;
        }

        AnyDoorSettingsState service = anyDoorSettingsStateOpt.get();
        BiConsumer<String, Exception> openExcConsumer = (url, e) -> NotifierUtil.notifyError(project, "call " + url + " error [ " + e.getMessage() + " ]");


        if (method.parameterTypeNames().isEmpty()) {
            String jsonDtoStr = getJsonDtoStr(project, method.className(), method.methodName(), method.parameterTypeNames(), "{}", !service.enableAsyncExecute, null);
            openAnyDoor(project, jsonDtoStr, service, openExcConsumer);
            okAction.run();
        } else {
            String cacheKey = AnyDoorActionUtil.genCacheKey(method.className(), method.methodName(), method.parameterTypeNames());
            if (service.enableNewUI) {
                doUseNewUI(service, project, method, cacheKey, okAction, null);
                return;
            }
            TextAreaDialog dialog = new TextAreaDialog(project, String.format("fill method(%s) param", method.methodName()), ReadAction.compute(psiMethod::getParameterList), service.getCache(cacheKey), service);
            dialog.setOkAction(() -> {
                okAction.run();
                if (dialog.isChangePid()) {
                    service.pid = dialog.getPid();
                }
                String text = dialog.getText();
                ParamCacheDto paramCacheDto = new ParamCacheDto(dialog.getRunNum(), dialog.getIsConcurrent(), text);
                service.putCache(cacheKey, paramCacheDto);
                text = method.prepareContent(text);
                text = JsonUtil.compressJson(text);
                String jsonDtoStr = getJsonDtoStr(project, method.className(), method.methodName(), method.parameterTypeNames(), text, !service.enableAsyncExecute, paramCacheDto);
                openAnyDoor(project, jsonDtoStr, service, openExcConsumer);
            });
            dialog.show();
        }
    }

    public boolean useNewUI(AnyDoorSettingsState service, Project project, PsiClass psiClass, PsiMethod psiMethod, String cacheKey, Runnable okAction) {
        if (service.enableNewUI) {
            doUseNewUI(service, project, psiClass, psiMethod, cacheKey, okAction, null);
            return true;
        }
        return false;
    }

    public void doUseNewUI(AnyDoorSettingsState service, Project project, PsiClass psiClass, PsiMethod psiMethod, String cacheKey, Runnable okAction, @Nullable Long selectedId) {
        doUseNewUI(service, project, MethodInvocationSnapshot.capture(psiClass, psiMethod), cacheKey, okAction, selectedId);
    }

    private void doUseNewUI(AnyDoorSettingsState service, Project project, MethodInvocationSnapshot method, String cacheKey, Runnable okAction, @Nullable Long selectedId) {
        ParamCacheDto cache = service.getCache(cacheKey);
        DataContext instance = DataContext.instance(project);
        MainUI mainUI = new MainUI(method.methodName(), project);

        instance.getExecuteDataContextAsync(
                method.className(),
                method.qualifiedMethodName(),
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

            text = method.prepareContent(text);
            String jsonDtoStr = getJsonDtoStr(project, method.className(), method.methodName(), method.parameterTypeNames(), text, !service.enableAsyncExecute, paramCacheDto);
            openAnyDoor(project, jsonDtoStr, service, (url, e) -> NotifierUtil.notifyError(project, "call " + url + " error [ " + e.getMessage() + " ]"));
        });
        mainUI.show();
    }

    private static void openAnyDoor(Project project, String jsonDtoStr, AnyDoorSettingsState service, BiConsumer<String, Exception> errHandle) {
        BeanSelectionService.execute(project, jsonDtoStr, service, errHandle);
    }

    /** Shared execution bridge for .anydoor request files. */
    public static void executeDirect(Project project, PsiClass psiClass, PsiMethod psiMethod, String content, @Nullable Long pid,
                                     @Nullable String resultPath, BiConsumer<String, Exception> errHandle) {
        executeDirect(project, psiClass, psiMethod, content, pid, resultPath, null, null, errHandle);
    }

    public static void executeDirect(Project project, PsiClass psiClass, PsiMethod psiMethod, String content, @Nullable Long pid,
                                     @Nullable String resultPath, @Nullable String beanName, @Nullable String beanContextId,
                                     BiConsumer<String, Exception> errHandle) {
        executeDirect(project, psiClass, psiMethod, content, pid, resultPath, beanName, beanContextId, errHandle, () -> { });
    }

    public static void executeDirect(Project project, PsiClass psiClass, PsiMethod psiMethod, String content, @Nullable Long pid,
                                     @Nullable String resultPath, @Nullable String beanName, @Nullable String beanContextId,
                                     BiConsumer<String, Exception> errHandle, Runnable onDispatch) {
        executeDirect(project, psiClass, psiMethod, content, pid, resultPath, beanName, beanContextId, null, errHandle, onDispatch);
    }

    public static void executeDirect(Project project, PsiClass psiClass, PsiMethod psiMethod, String content, @Nullable Long pid,
                                     @Nullable String resultPath, @Nullable String beanName, @Nullable String beanContextId,
                                     @Nullable String classLoaderId, BiConsumer<String, Exception> errHandle, Runnable onDispatch) {
        AnyDoorSettingsState service = project.getService(AnyDoorSettingsState.class);
        MethodInvocationSnapshot method = MethodInvocationSnapshot.capture(psiClass, psiMethod);
        if (pid != null) service.pid = pid;
        String body = JsonUtil.compressJson(content);
        body = method.prepareContent(body);
        // A request file is interactive: synchronous mode is required to obtain a deterministic result.
        JsonObject dto = JsonParser.parseString(getJsonDtoStr(project, method.className(), method.methodName(),
                method.parameterTypeNames(), body, true, null)).getAsJsonObject();
        if (resultPath != null) dto.addProperty("resultPath", resultPath);
        if (beanName != null && !beanName.isBlank()) dto.addProperty("beanName", beanName);
        if (beanContextId != null && !beanContextId.isBlank()) dto.addProperty("beanContextId", beanContextId);
        if (classLoaderId != null && !classLoaderId.isBlank()) dto.addProperty("classLoaderId", classLoaderId);
        BeanSelectionService.execute(project, dto.toString(), service, errHandle, onDispatch);
    }

    @NotNull
    static String getJsonDtoStr(Project project, String className, String methodName, List<String> paramTypeNameList, String content, boolean isSync, @Nullable ParamCacheDto paramCacheDto) {
        JsonObject jsonObjectReq = new JsonObject();
        jsonObjectReq.addProperty("content", content);
        jsonObjectReq.addProperty("methodName", methodName);
        jsonObjectReq.addProperty("className", className);
        jsonObjectReq.addProperty("sync", isSync);
        if (paramCacheDto != null) {
            jsonObjectReq.addProperty("num", paramCacheDto.getRunNum());
            jsonObjectReq.addProperty("concurrent", paramCacheDto.getConcurrent());
        }
        jsonObjectReq.add("parameterTypes", JsonUtil.toJsonArray(paramTypeNameList));

        String anyDoorJarPath = ImportNewUtil.getPluginLibPath(AnyDoorInfo.ANY_DOOR_JAR);
        String dependenceJarFilePath = ImportNewUtil.getPluginAgentPath(AnyDoorInfo.ANY_DOOR_ALL_DEPENDENCE_JAR);
        String anyDoorCommonJarPath = ImportNewUtil.getPluginLibPath(AnyDoorInfo.ANY_DOOR_COMMON_JAR);
        jsonObjectReq.add("jarPaths", JsonUtil.toJsonArray(List.of(anyDoorJarPath, dependenceJarFilePath, anyDoorCommonJarPath)));
        jsonObjectReq.addProperty("projectBasePath", project.getBasePath());
        AnyDoorSettingsState settings = project.getService(AnyDoorSettingsState.class);
        jsonObjectReq.addProperty("jsonTimezone", settings.jsonTimezone);
        jsonObjectReq.addProperty("jsonDateTimeFormat", settings.jsonDateTimeFormat);
        return jsonObjectReq.toString();
    }



}
