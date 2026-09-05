package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.slayerspeed.model.*;
import com.slayerspeed.tracking.ActiveTimeTracker;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimingMigrationTest
{
    @Test public void migrationPreservesOriginalAndSeparatesNewTimingRuns()
    {
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        String original = "{\"schemaVersion\":4,\"statisticsByTaskKey\":{}}";
        storage.write(TaskHistoryRepository.DATA_KEY, original);
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
        repo.loadProfile();
        assertEquals(original, storage.read("historyBackup"));
        assertTrue(storage.read(TaskHistoryRepository.DATA_KEY).contains("\"schemaVersion\":6"));
        ActiveTask legacy = new ActiveTask("Gargoyles", null, 100, 100, 1000);
        legacy.addProgressUnits(100);
        legacy.addActiveMillis(3600000);
        repo.saveRun(legacy.finish(TaskRunStatus.COMPLETED, 4000000), false, 50);
        ActiveTask segmented = new ActiveTask("Gargoyles", null, 100, 100, 1000);
        segmented.setTimingPolicy(1);
        ActiveTimeTracker clock = new ActiveTimeTracker();
        segmented.addProgressUnits(1);
        clock.recordActivity(segmented, 2000, 5);
        segmented.addProgressUnits(1);
        clock.recordActivity(segmented, 62000, 5);
        repo.saveRun(segmented.finish(TaskRunStatus.COMPLETED, 63000), false, 50);
        assertEquals(2, repo.allStatistics(false).size());
        assertEquals(100, repo.find(new TaskKey("Gargoyles", null, "", 0), false).getTotalTaskProgressUnits());
        assertEquals(1, repo.find(new TaskKey("Gargoyles", null, "", 1), false).getTotalTaskProgressUnits());
        repo.loadProfile();
        assertEquals(TaskHistoryRepository.LoadState.LOADED, repo.getLoadState());
        assertEquals(2, repo.allStatistics(false).size());
    }

    @Test public void rejectsCoercedNumbersOverflowAndInvalidCheckpoint()
    {
        Gson gson = new Gson();
        for (String json : new String[] {
            "{\"schemaVersion\":\"6\"}",
            "{\"schemaVersion\":6,\"checkpointedActiveTask\":{\"id\":\"x\",\"taskName\":\"Gargoyles\",\"rateUnits\":\"-1\"}}",
            "{\"schemaVersion\":6,\"checkpointedActiveTask\":{\"id\":\"x\",\"taskName\":\"Gargoyles\",\"startedAtMillis\":9223372036854775808}}",
            "{\"schemaVersion\":6,\"checkpointedActiveTask\":{\"id\":\"x\",\"taskName\":\"<html>bad\",\"timingPolicy\":9}}"
        })
        {
            try { HistoryValidator.decode(json, gson); fail("Unsafe import accepted: " + json); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
