package io.github.lgp547.anydoorplugin.dialog.perf;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshRequestStateTest {

    @Test
    void schedulesNewKeyOnceAndSkipsDuplicateKeyAtomically() {
        RefreshRequestState state = new RefreshRequestState();

        RefreshRequestState.ScheduleResult first = state.schedule("demo.Service");
        RefreshRequestState.ScheduleResult duplicate = state.schedule("demo.Service");
        RefreshRequestState.ScheduleResult secondKey = state.schedule("demo.OtherService");

        assertTrue(first.shouldSchedule());
        assertFalse(duplicate.shouldSchedule());
        assertEquals(first.token(), duplicate.token());
        assertTrue(secondKey.shouldSchedule());
    }

    @Test
    void invalidatesOlderTokenWhenSameKeyIsScheduledAgain() {
        RefreshRequestState state = new RefreshRequestState();

        RefreshRequestState.ScheduleResult first = state.schedule("demo.Service");
        state.schedule("demo.OtherService");
        RefreshRequestState.ScheduleResult latest = state.schedule("demo.Service");

        assertFalse(state.isLatest("demo.Service", first.token()));
        assertTrue(state.isLatest("demo.Service", latest.token()));
    }

    @Test
    void returnsSingleScheduledTokenAcrossConcurrentDuplicateRequests() throws Exception {
        RefreshRequestState state = new RefreshRequestState();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<RefreshRequestState.ScheduleResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return state.schedule("demo.Service");
            });
        }

        try {
            List<Future<RefreshRequestState.ScheduleResult>> futures = new ArrayList<>();
            for (Callable<RefreshRequestState.ScheduleResult> task : tasks) {
                futures.add(executor.submit(task));
            }

            ready.await();
            start.countDown();

            int scheduledCount = 0;
            Long sharedToken = null;
            for (Future<RefreshRequestState.ScheduleResult> future : futures) {
                RefreshRequestState.ScheduleResult result = future.get();
                if (result.shouldSchedule()) {
                    scheduledCount++;
                }
                if (sharedToken == null) {
                    sharedToken = result.token();
                } else {
                    assertEquals(sharedToken.longValue(), result.token());
                }
            }

            assertEquals(1, scheduledCount);
            assertTrue(state.isLatest("demo.Service", sharedToken));
        } finally {
            executor.shutdownNow();
        }
    }
}
