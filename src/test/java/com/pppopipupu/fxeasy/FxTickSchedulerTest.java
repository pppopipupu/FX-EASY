package com.pppopipupu.fxeasy;

import module java.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxTickSchedulerTest {

    @Test
    void runsOnTheDueTickAndNotBefore() {
        FxTickScheduler.clearServer();
        List<Integer> runs = new ArrayList<>();
        FxTickScheduler.scheduleServer(2, () -> runs.add(2));
        FxTickScheduler.tickServer();
        assertTrue(runs.isEmpty());
        FxTickScheduler.tickServer();
        assertEquals(List.of(2), runs);
    }

    @Test
    void aTaskScheduledWhileTickingWaitsForTheNextTick() {
        FxTickScheduler.clearClient();
        List<String> runs = new ArrayList<>();
        FxTickScheduler.scheduleClient(0, () -> {
            runs.add("first");
            FxTickScheduler.scheduleClient(0, () -> runs.add("second"));
        });
        FxTickScheduler.tickClient();
        assertEquals(List.of("first"), runs);
        FxTickScheduler.tickClient();
        assertEquals(List.of("first", "second"), runs);
    }

    @Test
    void clearingDropsPendingTasks() {
        FxTickScheduler.clearServer();
        List<String> runs = new ArrayList<>();
        FxTickScheduler.scheduleServer(5, () -> runs.add("run"));
        FxTickScheduler.clearServer();
        for (int i = 0; i < 6; i++) {
            FxTickScheduler.tickServer();
        }
        assertTrue(runs.isEmpty());
    }
}
