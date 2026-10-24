package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.FilenameIndex;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.ProcessingContext;
import java.util.LinkedHashSet;
import java.util.Set;

/** Contextual completion without replacing the user's text-first request format. */
public final class AnyDoorCompletionContributor extends CompletionContributor {
    public AnyDoorCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(AnyDoorLanguage.INSTANCE), new CompletionProvider<>() {
            @Override protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) { complete(parameters, result); }
        });
    }
    private static void complete(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        final CompletionResultSet output=result.withPrefixMatcher("");
        String text = parameters.getEditor().getDocument().getText().replace(CompletionUtilCore.DUMMY_IDENTIFIER, "").replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "");
        int offset = parameters.getOffset();
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        String line = text.substring(lineStart, Math.min(offset, text.length()));
        int variableStart = line.lastIndexOf("{{");
        if (variableStart >= 0 && line.indexOf("}}", variableStart) < 0) {
            java.util.regex.Matcher variables = java.util.regex.Pattern.compile("(?m)^\\s*@([\\w.-]+)\\s*=").matcher(text);
            while (variables.find()) output.addElement(variable(variables.group(1), "当前文件变量"));
            output.addElement(variable("now.iso", "内置时间变量"));
            output.addElement(variable("faker.uuid", "内置生成器"));
            return;
        }
        if (line.trim().startsWith("@")) {
            for (String directive : new String[]{"@name = ", "@pid = ", "@target = ", "@timeout = 10s", "@mode = live", "@confirm = destructive"}) output.addElement(LookupElementBuilder.create(directive));
            return;
        }
        String trimmedLine = line.trim();
        if (!(trimmedLine.equals("RUN") || trimmedLine.startsWith("RUN "))) return;
        String target = trimmedLine.length() == 3 ? "" : trimmedLine.substring(4);
        int hash = target.indexOf('#');
        if (hash < 0) {
            String prefix = target.substring(target.lastIndexOf(' ') + 1);
            var project = parameters.getPosition().getProject();
            ReadAction.run(() -> {
                String simplePrefix = prefix.substring(prefix.lastIndexOf('.') + 1);
                Set<String> seen = new LinkedHashSet<>();
                PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
                for (String shortName : cache.getAllClassNames()) {
                    if (!shortName.startsWith(simplePrefix)) continue;
                    for (PsiClass type : cache.getClassesByName(shortName, GlobalSearchScope.allScope(project))) {
                        String qualifiedName = type.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.startsWith(prefix) && seen.add(qualifiedName)) {
                            output.addElement(LookupElementBuilder.create(qualifiedName).withTypeText(type.getName(), true));
                            if (seen.size() >= 80) return;
                        }
                    }
                }
                // During indexing (and in freshly opened projects) the short-name cache can be
                // temporarily empty.  Files in project content are still available through PSI,
                // so use that narrow fallback instead of silently offering no project symbols.
                if (seen.isEmpty()) {
                    for (var virtualFile : FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))) {
                        var psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                        if (!(psiFile instanceof PsiClassOwner owner)) continue;
                        for (PsiClass type : owner.getClasses()) {
                            String qualifiedName = type.getQualifiedName();
                            if (qualifiedName != null && qualifiedName.startsWith(prefix) && seen.add(qualifiedName)) {
                                output.addElement(LookupElementBuilder.create(qualifiedName).withTypeText(type.getName(), true));
                                if (seen.size() >= 80) return;
                            }
                        }
                    }
                }
            });
            return;
        }
        String className = target.substring(0, hash);
        PsiClass type = ReadAction.compute(() -> JavaPsiFacade.getInstance(parameters.getPosition().getProject()).findClass(className, GlobalSearchScope.allScope(parameters.getPosition().getProject())));
        if (type == null) return;
        for (PsiMethod method : type.getAllMethods()) {
            String params=java.util.Arrays.stream(method.getParameterList().getParameters()).map(parameter -> parameter.getType().getPresentableText()).collect(java.util.stream.Collectors.joining(", "));
            output.addElement(LookupElementBuilder.create(method.getName() + "(" + params + ")").withTypeText(method.getReturnType() == null ? "void" : method.getReturnType().getPresentableText(), true));
        }
    }
    private static LookupElementBuilder variable(String name, String type) { return LookupElementBuilder.create(name + "}}").withPresentableText("{{" + name + "}}").withTypeText(type, true); }
}
