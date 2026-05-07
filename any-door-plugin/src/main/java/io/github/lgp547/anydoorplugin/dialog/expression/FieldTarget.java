package io.github.lgp547.anydoorplugin.dialog.expression;

import java.util.Objects;

public record FieldTarget(
        String rootParamName,
        String jsonPath,
        String displayPath,
        String declaredType,
        String currentJsonValue,
        TargetSource source
) {
    public FieldTarget {
        Objects.requireNonNull(rootParamName);
        Objects.requireNonNull(jsonPath);
        Objects.requireNonNull(displayPath);
        Objects.requireNonNull(declaredType);
        Objects.requireNonNull(source);
    }

    public enum TargetSource {
        TREE,
        CARET
    }
}
