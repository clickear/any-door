package io.github.lgp547.anydoorplugin.dialog.expression;

import java.util.Objects;

public record FieldExpressionCacheKey(
        String methodKey,
        String jsonPath
) {
    public FieldExpressionCacheKey {
        Objects.requireNonNull(methodKey);
        Objects.requireNonNull(jsonPath);
    }
}
