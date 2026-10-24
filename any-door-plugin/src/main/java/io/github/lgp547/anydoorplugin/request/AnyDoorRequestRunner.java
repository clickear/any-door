package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.lgp547.anydoorplugin.action.AnyDoorPerformed;
import io.github.lgp547.anydoorplugin.settings.AnyDoorSettingsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.UUID;

/** Single execution path for action, gutter and future in-editor results. */
public final class AnyDoorRequestRunner {
    private AnyDoorRequestRunner() { }
    public static String run(Project project, String text) {
        try {
            AnyDoorRequest request = AnyDoorRequestParser.parse(text);
            String result = runRequest(project, request);
            RequestHistoryService.instance(project).add(request.name(), "PID " + (request.pid() == null ? project.getService(AnyDoorSettingsState.class).pid : request.pid()), result, toText(request));
            return result;
        } catch (Exception exception) { return "失败：" + exception.getMessage(); }
    }
    public static String runAt(Project project, String text, int offset) { return runRequest(project, AnyDoorRequestParser.parseAt(text, offset)); }
    public static String runAll(Project project, String text) {
        String result = "";
        for (AnyDoorRequest request : AnyDoorRequestParser.parseAll(text)) result = runRequest(project, request);
        return result;
    }
    private static String runRequest(Project project, AnyDoorRequest request) {
        if ("dry-run".equalsIgnoreCase(request.mode())) return "Dry run：参数校验通过，未调用目标 JVM";
        if (isDestructive(request) && !request.confirmDestructive()) return "失败：该方法可能有副作用，请添加 @confirm = destructive";
        if (request.confirmDestructive() && Messages.showYesNoDialog(project, "确认调用可能产生副作用的方法 " + request.methodName() + "？", "AnyDoor 安全确认", null) != Messages.YES) return "已取消";
        String result = ReadAction.compute(() -> resolveAndSend(project, request));
        RequestHistoryService.instance(project).add(request.name(), "PID " + (request.pid() == null ? project.getService(AnyDoorSettingsState.class).pid : request.pid()), result);
        return result;
    }
    private static String resolveAndSend(Project project, AnyDoorRequest request) {
        PsiClass type = JavaPsiFacade.getInstance(project).findClass(request.className(), GlobalSearchScope.allScope(project));
        if (type == null) return "失败：找不到类 " + request.className();
        PsiMethod[] methods = type.findMethodsByName(request.methodName(), true);
        if (methods.length != 1) return methods.length == 0 ? "失败：找不到方法 " + request.methodName() : "失败：方法重载不明确";
        String content = resolveVariables(project, request.json(), request.variables());
        File resultFile = new File(project.getBasePath(), ".idea/any-door/results/" + UUID.randomUUID() + ".json");
        resultFile.getParentFile().mkdirs();
        AnyDoorPerformed.executeDirect(project, type, methods[0], content, request.pid(), resultFile.getAbsolutePath(), request.beanName(), request.beanContextId(), request.classLoaderId(), (target, error) ->
                RequestHistoryService.instance(project).add(request.name(), target, "失败：" + error.getMessage()),
                () -> waitForResult(project, request, resultFile));
        return "正在选择 Bean 或发送调用";
    }
    private static boolean isDestructive(AnyDoorRequest request) { return request.methodName().toLowerCase().matches(".*(delete|remove|update|save|create|send|publish|cancel).*" ); }
    private static String toText(AnyDoorRequest request) { return "### @name " + request.name() + "\n" + request.variables().entrySet().stream().map(e -> "@" + e.getKey() + " = " + e.getValue()).collect(java.util.stream.Collectors.joining("\n")) + "\nRUN " + request.className() + "#" + request.methodName() + "\n" + request.json(); }
    private static String resolveVariables(Project project, String json, java.util.Map<String, String> vars) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}").matcher(json);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = vars.get(key);
            if (value == null && key.startsWith("last.")) value = RequestResultService.instance(project).get(key.substring(5));
            if (value == null) value = System.getenv(key);
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
        }
        matcher.appendTail(output); return output.toString();
    }
    private static void waitForResult(Project project, AnyDoorRequest request, File file) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long timeout = System.currentTimeMillis() + Math.max(1, request.timeoutSeconds() == null ? 10 : request.timeoutSeconds()) * 1000L;
            while (System.currentTimeMillis() < timeout && !file.exists()) try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
            String result = "超时：目标 JVM 未在限定时间内返回结果";
            if (file.exists()) {
                try { result = FileUtil.loadFile(file); } catch (java.io.IOException ignored) { result = "读取目标 JVM 返回结果失败"; }
            }
            RequestResultService.instance(project).put(request.name(), result);
            RequestHistoryService.instance(project).add(request.name(), "结果", result.length() > 120 ? result.substring(0, 120) + "…" : result);
        });
    }
}
