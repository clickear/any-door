package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Ensures gutter registration is alive before the first request file is opened. */
public final class AnyDoorRequestStartupActivity implements StartupActivity.DumbAware {
    @Override public void runActivity(@NotNull Project project) { project.getService(AnyDoorRequestGutterService.class).installOpenFiles(); }
}
