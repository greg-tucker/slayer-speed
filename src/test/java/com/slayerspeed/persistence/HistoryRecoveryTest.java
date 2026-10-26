package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.slayerspeed.model.ActiveTask;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryRecoveryTest
{
    static final class MemoryStorage implements ProfileStorage
    {
        String profile = "a";
        final Map<String, String> values = new HashMap<>();
        public String profileKey() { return profile; }
        public String read(String key) { return values.get(profile + ":" + key); }
        public void write(String key, String value) { values.put(profile + ":" + key, value); }
        public void remove(String key) { values.remove(profile + ":" + key); }
    }

    @Test public void invalidHistorySurvivesCheckpointAndReset()
    {
        for (String original : new String[] {"{bad", "{\"schemaVersion\":999}",
            "{\"schemaVersion\":5,\"statisticsByTaskKey\":{\"bad\":null}}", "null"})
        {
            MemoryStorage storage = new MemoryStorage();
            storage.write(TaskHistoryRepository.DATA_KEY, original);
            TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
            repo.loadProfile();
            assertEquals(TaskHistoryRepository.LoadState.RECOVERY_REQUIRED, repo.getLoadState());
            repo.saveCheckpoint(new ActiveTask("Gargoyles", null, 100, 100, 1000));
            repo.deleteAll();
            assertEquals(original, storage.read(TaskHistoryRepository.DATA_KEY));
            assertEquals(original, repo.getRecoveryPayload());
            assertFalse(repo.isPersistenceAvailable());
        }
    }

    @Test public void oversizedOriginalCanBeRecoveredWithoutLosingItsBackup()
    {
        MemoryStorage storage = new MemoryStorage();
        String original = "x".repeat(HistoryValidator.MAX_CHARACTERS + 1);
        storage.write(TaskHistoryRepository.DATA_KEY, original);
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
        repo.loadProfile();
        assertEquals(TaskHistoryRepository.LoadState.RECOVERY_REQUIRED, repo.getLoadState());
        TaskHistoryRepository.ImportPreview preview = repo.previewImport(
            "{\"schemaVersion\":6,\"statisticsByTaskKey\":{}}", TaskHistoryRepository.ImportMode.REPLACE, 50);
        repo.applyImport(preview);
        assertEquals(original, storage.read("historyUnreadableBackup"));
        assertTrue(repo.isPersistenceAvailable());
    }

    @Test public void failedWritesAreVisibleAndRetryWithoutLosingTheTask()
    {
        MemoryStorage backing = new MemoryStorage();
        boolean[] fail = {true};
        ProfileStorage storage = new ProfileStorage()
        {
            public String profileKey() { return backing.profileKey(); }
            public String read(String key) { return backing.read(key); }
            public void remove(String key) { backing.remove(key); }
            public void write(String key, String value)
            {
                if (fail[0]) { throw new IllegalStateException("Disk unavailable"); }
                backing.write(key, value);
            }
        };
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
        repo.loadProfile();
        ActiveTask task = new ActiveTask("Gargoyles", null, 100, 100, 1000);
        repo.saveCheckpoint(task);
        assertFalse(repo.isPersistenceAvailable());
        assertTrue(repo.getStorageMessage().contains("could not be saved"));
        assertSame(task, repo.getCheckpoint());
        fail[0] = false;
        repo.saveCheckpoint(task);
        assertTrue(repo.isPersistenceAvailable());
        assertTrue(backing.read(TaskHistoryRepository.DATA_KEY).contains("Gargoyles"));
    }

    @Test public void accountSwitchClearsRecoveryAndPreventsStaleWrites()
    {
        MemoryStorage storage = new MemoryStorage();
        storage.write(TaskHistoryRepository.DATA_KEY, "{bad");
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, new Gson());
        repo.loadProfile();
        storage.profile = "b";
        repo.saveCheckpoint(new ActiveTask("Gargoyles", null, 100, 100, 1000));
        assertNull(storage.read(TaskHistoryRepository.DATA_KEY));
        repo.loadProfile();
        assertNull(repo.getRecoveryPayload());
        assertTrue(repo.isPersistenceAvailable());
        repo.saveCheckpoint(new ActiveTask("Bloodveld", null, 100, 100, 1000));
        assertTrue(storage.read(TaskHistoryRepository.DATA_KEY).contains("Bloodveld"));
        repo.loadProfile();
        assertEquals("Bloodveld", repo.getCheckpoint().getTaskName());
        storage.profile = "a";
        assertEquals("{bad", storage.read(TaskHistoryRepository.DATA_KEY));
    }
}
