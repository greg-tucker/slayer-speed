package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.slayerspeed.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryTransferTest
{
    private final Gson gson = new Gson();
    private TaskRun run(String id, int units)
    {
        return new TaskRun(id, "Gargoyles", null, 100, 0, true, units, units,
            1000, 0, 60000, 0, 1000, 61000, TaskRunStatus.COMPLETED);
    }

    @Test public void replaceRoundTripPreservesActiveTaskAndCreatesBackup()
    {
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, gson);
        repo.loadProfile();
        repo.saveRun(run("one", 100), true, 50);
        String export = repo.exportHistory(false);
        repo.deleteAll();
        assertNotNull(repo.getBackup());
        ActiveTask active = new ActiveTask("Bloodveld", null, 100, 70, 100000);
        repo.saveCheckpoint(active);
        repo.applyImport(repo.previewImport(export, TaskHistoryRepository.ImportMode.REPLACE, 50));
        assertSame(active, repo.getCheckpoint());
        assertEquals(100, repo.find(new TaskKey("Gargoyles", null), true).getTotalTaskProgressUnits());
    }

    @Test public void mergeIsIdempotentAndRejectsConflictingOrPrunedTotals()
    {
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, gson);
        repo.loadProfile(); repo.saveRun(run("one", 100), true, 50);
        String export = repo.exportHistory(false);
        repo.applyImport(repo.previewImport(export, TaskHistoryRepository.ImportMode.MERGE, 50));
        assertEquals(100, repo.find(new TaskKey("Gargoyles", null), true).getTotalTaskProgressUnits());
        String conflict = export.replace("\"taskProgressUnits\":100", "\"taskProgressUnits\":99");
        try { repo.previewImport(conflict, TaskHistoryRepository.ImportMode.MERGE, 50); fail(); }
        catch (IllegalArgumentException expected) {}
        repo.saveRun(run("two", 100), true, 1);
        try { repo.previewImport(export, TaskHistoryRepository.ImportMode.MERGE, 50); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("unretained")); }
    }

    @Test public void stalePreviewAndWrongAccountCannotCommit()
    {
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, gson);
        repo.loadProfile(); repo.saveRun(run("one", 100), true, 50);
        String export = repo.exportHistory(false);
        TaskHistoryRepository.ImportPreview preview = repo.previewImport(export, TaskHistoryRepository.ImportMode.REPLACE, 50);
        repo.saveRun(run("two", 100), true, 50);
        try { repo.applyImport(preview); fail(); } catch (IllegalStateException expected) {}
        preview = repo.previewImport(export, TaskHistoryRepository.ImportMode.REPLACE, 50);
        storage.profile = "other";
        try { repo.applyImport(preview); fail(); } catch (IllegalStateException expected) {}
        assertNull(storage.read(TaskHistoryRepository.DATA_KEY));
    }

    @Test public void undoRestoresStatisticsAndMalformedImportDoesNotMutate()
    {
        HistoryRecoveryTest.MemoryStorage storage = new HistoryRecoveryTest.MemoryStorage();
        TaskHistoryRepository repo = TaskHistoryRepository.forStorage(storage, gson);
        repo.loadProfile(); TaskRun run = run("one", 100); repo.saveRun(run, true, 50);
        String before = repo.exportHistory(false);
        repo.deleteRun(run); assertTrue(repo.canUndoDeletion()); repo.undoDeletion();
        assertEquals(before, repo.exportHistory(false));
        try { repo.previewImport("{bad", TaskHistoryRepository.ImportMode.REPLACE, 50); fail(); }
        catch (RuntimeException expected) {}
        assertEquals(before, repo.exportHistory(false));
    }
}
