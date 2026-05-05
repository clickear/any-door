package io.github.lgp547.anydoorplugin.dialog.perf;

import java.util.Objects;

public final class RefreshRequestState {

    private final LatestRequestToken latestRequestToken = new LatestRequestToken();
    private volatile RequestSnapshot current = new RequestSnapshot(null, 0L);

    public synchronized ScheduleResult schedule(String key) {
        RequestSnapshot snapshot = current;
        if (Objects.equals(snapshot.key(), key)) {
            return new ScheduleResult(false, snapshot.token());
        }

        long token = latestRequestToken.nextToken();
        current = new RequestSnapshot(key, token);
        return new ScheduleResult(true, token);
    }

    public boolean isLatest(String key, long token) {
        RequestSnapshot snapshot = current;
        return Objects.equals(snapshot.key(), key) && snapshot.token() == token;
    }

    public String getCurrentKey() {
        return current.key();
    }

    public record ScheduleResult(boolean shouldSchedule, long token) {
    }

    private record RequestSnapshot(String key, long token) {
    }
}
