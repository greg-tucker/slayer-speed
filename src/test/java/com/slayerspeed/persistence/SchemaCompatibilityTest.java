package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskRunStatus;
import org.junit.Test;
import static org.junit.Assert.*;

public class SchemaCompatibilityTest
{
    @Test public void schemaFiveLoadsWithoutRewritingExistingHistory()
    {
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        String original = "{\"schemaVersion\":5,\"statisticsByTaskKey\":{}}";
        storage.write(TaskHistoryRepository.DATA_KEY, original);
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
        repo.loadProfile();
        assertEquals(TaskHistoryRepository.LoadState.LOADED, repo.getLoadState());
        assertEquals(original, storage.read(TaskHistoryRepository.DATA_KEY));
        assertNull(storage.read("historyBackup"));
    }

    @Test public void ordinaryDevelopmentHistoryReturnsToSchemaFiveWithBackup()
    {
        Gson gson = new Gson();
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, gson);
        repo.loadProfile();
        ActiveTask task = new ActiveTask("Gargoyles", null, 100, 100, 1000);
        task.addProgressUnits(100);
        task.addActiveMillis(3600000);
        repo.saveRun(task.finish(TaskRunStatus.COMPLETED, 4000000), false, 50);
        ActiveTask checkpoint = new ActiveTask("Bloodveld", null, 150, 100, 5000000);
        repo.saveCheckpoint(checkpoint);
        String original = storage.read(TaskHistoryRepository.DATA_KEY)
            .replace("\"schemaVersion\":5", "\"schemaVersion\":6,\"timingPolicy\":0");
        storage.write(TaskHistoryRepository.DATA_KEY, original);
        repo.loadProfile();
        assertEquals(TaskHistoryRepository.LoadState.LOADED, repo.getLoadState());
        assertEquals(original, storage.read("historyBackup"));
        assertTrue(storage.read(TaskHistoryRepository.DATA_KEY).contains("\"schemaVersion\":5"));
        assertEquals(100, repo.find(new TaskKey("Gargoyles", null), false).getTotalTaskProgressUnits());
        assertEquals(checkpoint.getId(), repo.getCheckpoint().getId());
        repo.loadProfile();
        assertEquals(TaskHistoryRepository.LoadState.LOADED, repo.getLoadState());
        assertEquals(original, storage.read("historyBackup"));
    }

    @Test public void experimentalHistoryAndCheckpointsArePreservedWithoutConversion()
    {
        for (String experimental : new String[] {
            "{\"schemaVersion\":6,\"statisticsByTaskKey\":{\"x\":{\"timingPolicy\":1}}}",
            "{\"schemaVersion\":6,\"checkpointedActiveTask\":{\"timingPolicy\":1}}"
        })
        {
            HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
            storage.write(TaskHistoryRepository.DATA_KEY, experimental);
            TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
            repo.loadProfile();
            assertEquals(TaskHistoryRepository.LoadState.RECOVERY_REQUIRED, repo.getLoadState());
            repo.saveCheckpoint(new ActiveTask("Gargoyles", null, 100, 100, 1000));
            assertEquals(experimental, storage.read(TaskHistoryRepository.DATA_KEY));
            assertEquals(experimental, repo.getRecoveryPayload());
        }
    }

    @Test public void rejectsCoercedNumbersOverflowAndInvalidCheckpoint()
    {
        for (String json : new String[] {
            "{\"schemaVersion\":\"5\"}",
            "{\"schemaVersion\":5,\"checkpointedActiveTask\":{\"id\":\"x\",\"taskName\":\"Gargoyles\",\"actualKills\":\"-1\"}}",
            "{\"schemaVersion\":5,\"checkpointedActiveTask\":{\"id\":\"x\",\"taskName\":\"Gargoyles\",\"startedAtMillis\":9223372036854775808}}",
            "{\"schemaVersion\":5,\"checkpointedActiveTask\":{\"id\":\"x\",\"taskName\":\"<html>bad\"}}"
        })
        {
            try { HistoryValidator.decode(json, new Gson()); fail("Unsafe import accepted: " + json); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
