package io.github.lgp547.anydoorplugin.request;

import com.intellij.lang.Language;

public final class AnyDoorLanguage extends Language {
    public static final AnyDoorLanguage INSTANCE = new AnyDoorLanguage();
    private AnyDoorLanguage() { super("AnyDoor"); }
}
