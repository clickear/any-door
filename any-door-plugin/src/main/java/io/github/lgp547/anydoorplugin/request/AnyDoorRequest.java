package io.github.lgp547.anydoorplugin.request;

import java.util.Map;

/** One independently executable block in an .anydoor file. */
public record AnyDoorRequest(String name, Long pid, String className, String methodName, String json,
                             Map<String, String> variables, String mode, Integer timeoutSeconds,
                             boolean confirmDestructive, int startOffset, int endOffset) { }
