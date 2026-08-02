package com.slayerspeed.persistence;

import com.google.inject.ImplementedBy;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.TaskRun;

@ImplementedBy(TaskHistoryRepository.class)
public interface TaskHistoryStore
{
	void loadProfile();

	void saveRun(TaskRun run, boolean separateByLocation, int maximumRecentRuns);

	void saveCheckpoint(ActiveTask activeTask);

	ActiveTask getCheckpoint();
}

