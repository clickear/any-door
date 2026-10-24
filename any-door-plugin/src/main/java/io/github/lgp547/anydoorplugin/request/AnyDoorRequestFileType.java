package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.fileTypes.LanguageFileType;
import io.github.lgp547.anydoorplugin.util.AnyDoorIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/** Human-editable method calls, inspired by IntelliJ HTTP client request files. */
public final class AnyDoorRequestFileType extends LanguageFileType {
    public static final AnyDoorRequestFileType INSTANCE = new AnyDoorRequestFileType();
    private AnyDoorRequestFileType() { super(AnyDoorLanguage.INSTANCE); }
    @Override public @NotNull String getName() { return "AnyDoor Request"; }
    @Override public @NotNull String getDescription() { return "AnyDoor method request"; }
    @Override public @NotNull String getDefaultExtension() { return "anydoor"; }
    @Override public Icon getIcon() { return AnyDoorIcons.default_icon; }
}
