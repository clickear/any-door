package io.github.lgp547.anydoorplugin.dialog.perf;

import java.util.concurrent.atomic.AtomicLong;

public final class LatestRequestToken {

    private final AtomicLong sequence = new AtomicLong();

    public long nextToken() {
        return sequence.incrementAndGet();
    }

    public boolean isCurrent(long token) {
        return sequence.get() == token;
    }
}
