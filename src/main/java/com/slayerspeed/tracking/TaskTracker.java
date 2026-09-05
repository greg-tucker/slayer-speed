package com.slayerspeed.tracking;

import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.persistence.TaskHistoryStore;
import javax.inject.Inject;

public class TaskTracker
{
	private static final long CHECKPOINT_INTERVAL_MILLIS = 15_000L;
	static final long COMPLETION_GRACE_MILLIS = 1_800L;

	private final TaskHistoryStore repository;
	private final ActiveTimeTracker activeTimeTracker;
	private ActiveTask activeTask;
	private boolean needsRebaselineAfterLoad;
    private boolean segmentedTiming;
    public void setSegmentedTiming(boolean enabled) { segmentedTiming = enabled; }

    public void toggleManualPause(long now)
    {
        if (activeTask == null || activeTask.getTimingPolicy() == 0) { return; }
        if (activeTask.isManualPaused()) { activeTask.resumeManually(now); }
        else { activeTask.pauseManually(); }
        checkpoint();
    }
	private long taskMissingSinceMillis;
	private long completionConfirmedAtMillis;

	@Inject
	public TaskTracker(TaskHistoryStore repository, ActiveTimeTracker activeTimeTracker)
	{
		this.repository = repository;
		this.activeTimeTracker = activeTimeTracker;
	}

	public void loadProfile()
	{
		repository.loadProfile();
		activeTask = repository.getCheckpoint();
		activeTimeTracker.pause(activeTask);
		needsRebaselineAfterLoad = activeTask != null;
		taskMissingSinceMillis = 0L;
		completionConfirmedAtMillis = activeTask == null ? 0L : activeTask.getCompletionConfirmedAtMillis();
	}

	public TaskUpdate observe(
		TaskSnapshot snapshot,
		long nowMillis,
		int idleTimeoutMinutes,
		boolean separateByLocation,
		int maximumRecentRuns)
	{
		if (activeTask == null)
		{
			if (snapshot.hasTask())
			{
				activeTask = start(snapshot, nowMillis);
				repository.saveCheckpoint(activeTask);
				return new TaskUpdate(0, true, null);
			}
			return TaskUpdate.none();
		}

		if (!snapshot.hasTask())
		{
			if (taskMissingSinceMillis == 0L)
			{
				taskMissingSinceMillis = nowMillis;
				return TaskUpdate.none();
			}

			long graceStartedAt = completionConfirmedAtMillis > 0L
				? completionConfirmedAtMillis
				: taskMissingSinceMillis;
			if (nowMillis - graceStartedAt < COMPLETION_GRACE_MILLIS)
			{
				return TaskUpdate.none();
			}

			TaskRunStatus status = completionConfirmedAtMillis > 0L
				? TaskRunStatus.COMPLETED
				: TaskRunStatus.UNKNOWN_INCOMPLETE;
			TaskRun ended = finish(status, nowMillis, separateByLocation, maximumRecentRuns);
			return new TaskUpdate(0, false, ended);
		}

		if (!sameAssignment(activeTask, snapshot))
		{
			TaskRun ended = finish(completionConfirmedAtMillis > 0L ? TaskRunStatus.COMPLETED : TaskRunStatus.REPLACED,
				nowMillis, separateByLocation, maximumRecentRuns);
			activeTask = start(snapshot, nowMillis);
			repository.saveCheckpoint(activeTask);
			return new TaskUpdate(0, true, ended);
		}

		taskMissingSinceMillis = 0L;
		if (completionConfirmedAtMillis > 0L)
		{
			// The Slayer service can expose its previous positive remainder for a tick after
			// the completion message. Do not overwrite the authoritative terminal zero.
			if (nowMillis - completionConfirmedAtMillis < COMPLETION_GRACE_MILLIS)
			{
				return TaskUpdate.none();
			}

			TaskRun ended = finish(TaskRunStatus.COMPLETED, nowMillis, separateByLocation, maximumRecentRuns);
			activeTask = start(snapshot, nowMillis);
			repository.saveCheckpoint(activeTask);
			return new TaskUpdate(0, true, ended);
		}

		if (needsRebaselineAfterLoad)
		{
			needsRebaselineAfterLoad = false;
			activeTask.setLastRemainingAmount(snapshot.getRemainingAmount());
			repository.saveCheckpoint(activeTask);
			return TaskUpdate.none();
		}

        int previous = activeTask.getLastRemainingAmount();
        int current = snapshot.getRemainingAmount();
        if (current > previous && snapshot.getInitialAmount() > 0 && current == snapshot.getInitialAmount())
        {
            TaskRun ended = finish(TaskRunStatus.REPLACED, nowMillis, separateByLocation, maximumRecentRuns);
            activeTask = start(snapshot, nowMillis);
            repository.saveCheckpoint(activeTask);
            return new TaskUpdate(0, true, ended);
        }
		activeTask.setLastRemainingAmount(current);
		int progress = Math.max(0, previous - current);
		if (progress > 0)
		{
			activeTask.addProgressUnits(progress);
			activeTimeTracker.recordActivity(activeTask, nowMillis, idleTimeoutMinutes);
			checkpointIfDue(nowMillis);
		}
		return progress > 0 ? new TaskUpdate(progress, false, null) : TaskUpdate.none();
	}

	public void applyAttribution(int literalKills, int slayerXp, long nowMillis, int idleTimeoutMinutes)
	{
		if (activeTask == null)
		{
			return;
		}
		activeTask.addActualKills(literalKills);
		activeTask.addKillSlayerXp(slayerXp);
		if (literalKills > 0 || slayerXp > 0)
		{
			activeTimeTracker.recordActivity(activeTask, nowMillis, idleTimeoutMinutes);
			checkpointIfDue(nowMillis);
		}
	}

	public void applyBonusXp(int slayerXp, long nowMillis, int idleTimeoutMinutes)
	{
		if (activeTask == null || completionConfirmedAtMillis == 0L || slayerXp <= 0)
		{
			return;
		}
		activeTask.addBonusSlayerXp(slayerXp);
		activeTimeTracker.recordActivity(activeTask, nowMillis, idleTimeoutMinutes);
		repository.saveCheckpoint(activeTask);
	}

	public void applyCannonballsUsed(int cannonballs, long nowMillis)
	{
		if (activeTask == null || cannonballs <= 0)
		{
			return;
		}
		activeTask.addCannonballsUsed(cannonballs);
		checkpointIfDue(nowMillis);
	}

	public int confirmCompletion(long nowMillis, int idleTimeoutMinutes)
	{
		if (activeTask == null || completionConfirmedAtMillis > 0L)
		{
			return 0;
		}

		int terminalProgress = Math.max(0, activeTask.getLastRemainingAmount());
		activeTask.addProgressUnits(terminalProgress);
		activeTask.setLastRemainingAmount(0);
		activeTimeTracker.recordActivity(activeTask, nowMillis, idleTimeoutMinutes);
		completionConfirmedAtMillis = nowMillis;
		activeTask.setCompletionConfirmedAtMillis(nowMillis);
		repository.saveCheckpoint(activeTask);
		return terminalProgress;
	}

	public boolean isCompletionPending()
	{
		return activeTask != null && completionConfirmedAtMillis > 0L;
	}

	public void pause()
	{
		activeTimeTracker.pause(activeTask);
		if (activeTask != null)
		{
			repository.saveCheckpoint(activeTask);
		}
	}

	public void checkpoint()
	{
		if (activeTask != null)
		{
			repository.saveCheckpoint(activeTask);
		}
	}

	public ActiveTask getActiveTask()
	{
		return activeTask;
	}

	private ActiveTask start(TaskSnapshot snapshot, long nowMillis)
	{
        ActiveTask task = new ActiveTask(
            snapshot.getTaskName(),
			snapshot.getTaskLocation(),
			snapshot.getInitialAmount(),
			snapshot.getRemainingAmount(),
            nowMillis);
        task.setTimingPolicy(segmentedTiming ? 1 : 0);
        return task;
    }

    private TaskRun finish(
		TaskRunStatus status,
		long nowMillis,
		boolean separateByLocation,
		int maximumRecentRuns)
	{
		TaskRun run = activeTask.finish(status, nowMillis);
		activeTask = null;
		needsRebaselineAfterLoad = false;
		taskMissingSinceMillis = 0L;
		completionConfirmedAtMillis = 0L;
		repository.saveRun(run, separateByLocation, maximumRecentRuns);
		return run;
	}

	private static boolean sameAssignment(ActiveTask task, TaskSnapshot snapshot)
	{
		return new TaskKey(task.getTaskName(), task.getTaskLocation()).equals(
			new TaskKey(snapshot.getTaskName(), snapshot.getTaskLocation()));
	}

	private void checkpointIfDue(long nowMillis)
	{
		if (activeTask.getLastCheckpointAtMillis() == 0
			|| nowMillis - activeTask.getLastCheckpointAtMillis() >= CHECKPOINT_INTERVAL_MILLIS)
		{
			activeTask.setLastCheckpointAtMillis(nowMillis);
			repository.saveCheckpoint(activeTask);
		}
	}
}
