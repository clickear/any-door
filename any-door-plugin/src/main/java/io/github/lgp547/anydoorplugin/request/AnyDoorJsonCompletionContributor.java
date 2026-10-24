package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.lgp547.anydoorplugin.util.JsonElementUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/** Variables inside an injected JSON body need a JSON-language contributor, not the host contributor. */
public final class AnyDoorJsonCompletionContributor extends CompletionContributor {
    private static final java.util.regex.Pattern RUN = java.util.regex.Pattern.compile("(?m)^\\s*RUN\\s+([\\w.$]+)#([\\w$]+)");
    public AnyDoorJsonCompletionContributor() { extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
        @Override protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiElement host=InjectedLanguageManager.getInstance(parameters.getPosition().getProject()).getInjectionHost(parameters.getOriginalFile().getViewProvider());
            if(!(host instanceof AnyDoorJsonBody))return;
            String text=parameters.getEditor().getDocument().getText().replace(CompletionUtilCore.DUMMY_IDENTIFIER,"").replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED,"");
            int offset=parameters.getOffset();int start=text.lastIndexOf('\n',Math.max(0,offset-1))+1;String line=text.substring(start,Math.min(offset,text.length()));
            if(line.lastIndexOf("{{")>=0) {
                var output=result.withPrefixMatcher("");var vars=java.util.regex.Pattern.compile("(?m)^\\s*@([\\w.-]+)\\s*=").matcher(host.getContainingFile().getText());
                while(vars.find())output.addElement(LookupElementBuilder.create(vars.group(1)+"}}").withPresentableText("{{"+vars.group(1)+"}}").withTypeText("当前文件变量",true));
                output.addElement(LookupElementBuilder.create("now.iso}}").withPresentableText("{{now.iso}}").withTypeText("内置时间变量",true));
                return;
            }
            if (!JsonElementUtil.isJsonKey(parameters.getPosition())) return;
            var output = result.withPrefixMatcher("");
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (PsiMethod method : targetMethods((AnyDoorJsonBody) host)) {
                for (var parameter : method.getParameterList().getParameters()) {
                    if (seen.add(parameter.getName())) {
                        output.addElement(LookupElementBuilder.create(parameter.getName())
                                .withTypeText(parameter.getType().getPresentableText(), true)
                                .withTailText("  method parameter", true)
                                .withInsertHandler((insertion, item) -> insertJsonKey(insertion, parameter.getName())));
                    }
                }
            }
        }
    }); }

    private static java.util.List<PsiMethod> targetMethods(AnyDoorJsonBody host) {
        String source = host.getContainingFile().getText();
        java.util.regex.Matcher runs = RUN.matcher(source);
        String className = null;
        String methodName = null;
        while (runs.find()) {
            if (runs.start() >= host.getTextRange().getStartOffset()) break;
            className = runs.group(1);
            methodName = runs.group(2);
        }
        if (className == null) return java.util.List.of();
        PsiClass type = JavaPsiFacade.getInstance(host.getProject()).findClass(className, GlobalSearchScope.allScope(host.getProject()));
        if (type == null) return java.util.List.of();
        return java.util.Arrays.asList(type.findMethodsByName(methodName, true));
    }

    private static void insertJsonKey(InsertionContext insertion, String name) {
        var document = insertion.getDocument();
        int start = insertion.getStartOffset();
        int end = insertion.getTailOffset();
        boolean insideQuotes = start > 0 && end < document.getTextLength()
                && document.getCharsSequence().charAt(start - 1) == '"'
                && document.getCharsSequence().charAt(end) == '"';
        if (insideQuotes) {
            document.insertString(end + 1, ": ");
            insertion.setTailOffset(end + 3);
        } else {
            document.replaceString(start, end, "\"" + name + "\": ");
            insertion.setTailOffset(start + name.length() + 4);
        }
    }
}
