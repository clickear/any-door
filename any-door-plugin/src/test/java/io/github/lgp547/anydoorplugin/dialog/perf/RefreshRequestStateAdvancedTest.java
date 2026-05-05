package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshRequestStateAdvancedTest {

    @Test
    void duplicateKeyDoesNotNeedAnotherSchedule() {
        RefreshRequestState state = new RefreshRequestState();
        RefreshRequestState.ScheduleResult first = state.schedule("demo.A");
        assertTrue(first.shouldSchedule());
        RefreshRequestState.ScheduleResult duplicate = state.schedule("demo.A");
        assertFalse(duplicate.shouldSchedule());
    }

    @Test
    void olderTokenBecomesInvalidAfterNewKeyIsScheduled() {
        RefreshRequestState state = new RefreshRequestState();
        RefreshRequestState.ScheduleResult first = state.schedule("demo.A");
        RefreshRequestState.ScheduleResult second = state.schedule("demo.B");
        assertFalse(state.isLatest("demo.A", first.token()));
        assertTrue(state.isLatest("demo.B", second.token()));
    }
}
