package com.slayerspeed.tracking;

import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.persistence.TaskHistoryStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TaskTrackerTest
{
	@Test
	public void tracksProgressAndCompletesNearZeroTask()
	{
		FakeStore store = new FakeStore();
		TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());

		tracker.observe(TaskSnapshot.active("Gargoyles", null, 2, 2), 1_000, 5, true, 50);
		TaskUpdate progress = tracker.observe(TaskSnapshot.active("Gargoyles", null, 2, 1), 2_000, 5, true, 50);
		assertEquals(1, tracker.confirmCompletion(2_500, 5));
		tracker.observe(TaskSnapshot.active("Gargoyles", null, 2, 1), 2_800, 5, true, 50);
		assertEquals(0, tracker.getActiveTask().getLastRemainingAmount());
		tracker.observe(TaskSnapshot.none(), 3_000, 5, true, 50);
		TaskUpdate completion = tracker.observe(TaskSnapshot.none(), 4_500, 5, true, 50);

		assertEquals(1, progress.getProgressDelta());
		assertNotNull(completion.getEndedRun());
		assertEquals(TaskRunStatus.COMPLETED, completion.getEndedRun().getStatus());
		assertEquals(2, completion.getEndedRun().getTaskProgressUnits());
		assertEquals(0, completion.getEndedRun().getEndingAmount());
		assertEquals(1, store.runs.size());
	}

	@Test
	public void nearZeroTaskWithoutCompletionEvidenceIsIncomplete()
	{
		FakeStore store = new FakeStore();
		TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());

		tracker.observe(TaskSnapshot.active("Gargoyles", null, 2, 2), 1_000, 5, true, 50);
		tracker.observe(TaskSnapshot.active("Gargoyles", null, 2, 1), 2_000, 5, true, 50);
		tracker.observe(TaskSnapshot.none(), 3_000, 5, true, 50);
		TaskUpdate ended = tracker.observe(
			TaskSnapshot.none(), 3_000 + TaskTracker.COMPLETION_GRACE_MILLIS, 5, true, 50);

		assertEquals(TaskRunStatus.UNKNOWN_INCOMPLETE, ended.getEndedRun().getStatus());
		assertEquals(1, ended.getEndedRun().getTaskProgressUnits());
	}

	@Test
	public void sameAssignmentAfterCompletionStartsANewRunAfterGrace()
	{
		FakeStore store = new FakeStore();
		TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());
		tracker.observe(TaskSnapshot.active("Gargoyles", null, 1, 1), 1_000, 5, true, 50);
		tracker.confirmCompletion(2_000, 5);

		TaskUpdate duringGrace = tracker.observe(
			TaskSnapshot.active("Gargoyles", null, 120, 120), 3_000, 5, true, 50);
		TaskUpdate afterGrace = tracker.observe(
			TaskSnapshot.active("Gargoyles", null, 120, 120), 4_000, 5, true, 50);

		assertEquals(false, duringGrace.isTaskStarted());
		assertEquals(true, afterGrace.isTaskStarted());
		assertEquals(TaskRunStatus.COMPLETED, afterGrace.getEndedRun().getStatus());
		assertEquals(120, tracker.getActiveTask().getLastRemainingAmount());
	}

	@Test
	public void completionEvidenceSurvivesCheckpointRecovery()
	{
		FakeStore store = new FakeStore();
		TaskTracker firstTracker = new TaskTracker(store, new ActiveTimeTracker());
		firstTracker.observe(TaskSnapshot.active("Gargoyles", null, 1, 1), 1_000, 5, true, 50);
		firstTracker.confirmCompletion(2_000, 5);

		TaskTracker recoveredTracker = new TaskTracker(store, new ActiveTimeTracker());
		recoveredTracker.loadProfile();
		recoveredTracker.observe(TaskSnapshot.none(), 4_000, 5, true, 50);
		TaskUpdate ended = recoveredTracker.observe(TaskSnapshot.none(), 4_600, 5, true, 50);

		assertEquals(TaskRunStatus.COMPLETED, ended.getEndedRun().getStatus());
	}

	@Test
	public void doesNotCountProgressThatOccurredWhilePluginWasStopped()
	{
		FakeStore store = new FakeStore();
		store.checkpoint = new ActiveTask("Dust devils", null, 150, 100, 1_000);
		TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());

		tracker.loadProfile();
		TaskUpdate firstObservation = tracker.observe(
			TaskSnapshot.active("Dust devils", null, 150, 80), 10_000, 5, true, 50);
		TaskUpdate nextKill = tracker.observe(
			TaskSnapshot.active("Dust devils", null, 150, 79), 11_000, 5, true, 50);

		assertEquals(0, firstObservation.getProgressDelta());
		assertEquals(1, nextKill.getProgressDelta());
		assertEquals(1, tracker.getActiveTask().getTaskProgressUnits());
	}

	@Test
	public void marksChangedTaskAsReplaced()
	{
		FakeStore store = new FakeStore();
		TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());
		tracker.observe(TaskSnapshot.active("Gargoyles", null, 100, 100), 1_000, 5, true, 50);

		TaskUpdate update = tracker.observe(
			TaskSnapshot.active("Abyssal demons", null, 150, 150), 2_000, 5, true, 50);

		assertEquals(TaskRunStatus.REPLACED, update.getEndedRun().getStatus());
		assertEquals("Abyssal demons", tracker.getActiveTask().getTaskName());
	}

	@Test
	public void carriesCannonballUsageIntoTheFinishedRun()
	{
		FakeStore store = new FakeStore();
		TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());
		tracker.observe(TaskSnapshot.active("Dagannoth", null, 1, 1), 1_000, 5, true, 50);
		tracker.applyCannonballsUsed(12, 1_500);
		tracker.confirmCompletion(2_000, 5);
		tracker.observe(TaskSnapshot.none(), 2_100, 5, true, 50);
		TaskUpdate ended = tracker.observe(TaskSnapshot.none(), 4_000, 5, true, 50);

		assertEquals(12, ended.getEndedRun().getCannonballsUsed());
	}

    @Test public void sameNameResetStartsSeparateRunWithoutCompletionClaim()
    {
        FakeStore store = new FakeStore();
        TaskTracker tracker = new TaskTracker(store, new ActiveTimeTracker());
        tracker.setSegmentedTiming(true);
        tracker.observe(TaskSnapshot.active("Gargoyles", null, 100, 100), 1000, 5, true, 50);
        tracker.observe(TaskSnapshot.active("Gargoyles", null, 100, 80), 61000, 5, true, 50);
        TaskUpdate update = tracker.observe(TaskSnapshot.active("Gargoyles", null, 100, 100), 62000, 5, true, 50);
        assertEquals(TaskRunStatus.REPLACED, update.getEndedRun().getStatus());
        assertEquals(1, tracker.getActiveTask().getTimingPolicy());
        assertEquals(0, tracker.getActiveTask().getTaskProgressUnits());
    }

    private static final class FakeStore implements TaskHistoryStore
	{
		private final List<TaskRun> runs = new ArrayList<>();
		private ActiveTask checkpoint;

		@Override
		public void loadProfile()
		{
		}

		@Override
		public void saveRun(TaskRun run, boolean separateByLocation, int maximumRecentRuns)
		{
			runs.add(run);
			checkpoint = null;
		}

		@Override
		public void saveCheckpoint(ActiveTask activeTask)
		{
			checkpoint = activeTask;
		}

		@Override
		public ActiveTask getCheckpoint()
		{
			return checkpoint;
		}
	}
}
