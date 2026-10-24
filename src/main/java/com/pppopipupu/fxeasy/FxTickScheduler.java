package com.pppopipupu.fxeasy;

import module java.base;

/// Runs a task on the server or client thread after a delay in ticks. Each queue belongs to one thread:
/// scheduling, ticking and clearing the server queue must happen on the server thread, and the client queue on
/// the client thread. A task that schedules another task starts it no earlier than the next tick.
public final class FxTickScheduler {

    private static final List<Task> SERVER_TASKS = new ArrayList<>();
    private static final List<Task> CLIENT_TASKS = new ArrayList<>();
    private static long serverTick;
    private static long clientTick;

    public static void scheduleServer(long delayTicks, Runnable action) {
        SERVER_TASKS.add(new Task(serverTick + Math.max(0L, delayTicks), action));
    }

    public static void scheduleClient(long delayTicks, Runnable action) {
        CLIENT_TASKS.add(new Task(clientTick + Math.max(0L, delayTicks), action));
    }

    public static void tickServer() {
        serverTick++;
        runDue(SERVER_TASKS, serverTick);
    }

    public static void tickClient() {
        clientTick++;
        runDue(CLIENT_TASKS, clientTick);
    }

    public static void clearServer() {
        SERVER_TASKS.clear();
        serverTick = 0L;
    }

    public static void clearClient() {
        CLIENT_TASKS.clear();
        clientTick = 0L;
    }

    private static void runDue(List<Task> tasks, long now) {
        List<Task> due = null;
        for (Iterator<Task> iterator = tasks.iterator(); iterator.hasNext(); ) {
            Task task = iterator.next();
            if (task.dueTick() <= now) {
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(task);
                iterator.remove();
            }
        }
        if (due == null) {
            return;
        }
        for (Task task : due) {
            try {
                task.action().run();
            } catch (RuntimeException error) {
                FxEasy.LOGGER.error("Scheduled FX task failed", error);
            }
        }
    }

    private record Task(long dueTick, Runnable action) {
    }

    private FxTickScheduler() {
    }
}
