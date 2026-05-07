package io.github.lgp547.anydoorplugin.dialog.event.impl;

import io.github.lgp547.anydoorplugin.dialog.event.ComponentEvent;
import io.github.lgp547.anydoorplugin.dialog.event.EventType;
import io.github.lgp547.anydoorplugin.dialog.expression.FieldTarget;

public class FieldTargetChangedEvent implements ComponentEvent {
    private final FieldTarget target;

    public FieldTargetChangedEvent(FieldTarget target) {
        this.target = target;
    }

    @Override
    public EventType getType() {
        return EventType.FIELD_TARGET_CHANGED;
    }

    public FieldTarget getTarget() {
        return target;
    }
}
