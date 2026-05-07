package io.github.lgp547.anydoorplugin.dialog.expression;

import java.util.Objects;

public record ExpressionRequest(
        String methodKey,
        FieldTarget target,
        String expression,
        String fullJsonText
) {
    public ExpressionRequest {
        Objects.requireNonNull(methodKey);
        Objects.requireNonNull(target);
        Objects.requireNonNull(expression);
        Objects.requireNonNull(fullJsonText);
    }
}
