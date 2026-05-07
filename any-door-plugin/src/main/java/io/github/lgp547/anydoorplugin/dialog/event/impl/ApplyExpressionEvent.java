package io.github.lgp547.anydoorplugin.dialog.event.impl;

import io.github.lgp547.anydoorplugin.dialog.event.ComponentEvent;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.expression.ExpressionRequest;

public class ApplyExpressionEvent implements ComponentEvent {
    private final ExpressionRequest request;

    public ApplyExpressionEvent(ExpressionRequest request) {
        this.request = request;
    }

    @Override
    public EventType getType() {
        return EventType.APPLY_EXPRESSION;
    }

    public ExpressionRequest getRequest() {
        return request;
    }
}
