package io.github.lgp547.anydoorplugin.request;

import com.intellij.json.JsonLanguage;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

/** Delegates request bodies to IntelliJ's JSON language rather than reimplementing JSON in AnyDoor. */
public final class AnyDoorJsonInjector implements LanguageInjector {
    @Override public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces places) {
        if (!(host instanceof AnyDoorJsonBody)) return;
        String text = host.getText();
        List<TextRange> comments = lineComments(text);
        int start = 0;
        for (TextRange comment : comments) {
            addJsonPlace(host, places, start, comment.getStartOffset());
            start = comment.getEndOffset();
        }
        addJsonPlace(host, places, start, text.length());
    }

    private static void addJsonPlace(PsiLanguageInjectionHost host, InjectedLanguagePlaces places, int start, int end) {
        if (start < end) places.addPlace(JsonLanguage.INSTANCE, new TextRange(start, end), null, null);
    }

    /** Keep comments editable and grey in the host, but do not feed them to strict JSON parsing. */
    private static List<TextRange> lineComments(String text) {
        List<TextRange> comments = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"' && !escaped) quoted = false;
                escaped = current == '\\' && !escaped;
                if (current != '\\') escaped = false;
                continue;
            }
            if (current == '"') { quoted = true; continue; }
            if (current == '/' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                int end = text.indexOf('\n', index);
                if (end < 0) end = text.length();
                comments.add(new TextRange(index, end));
                index = end;
            }
        }
        return comments;
    }
}
