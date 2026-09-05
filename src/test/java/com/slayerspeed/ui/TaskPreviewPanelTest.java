package com.slayerspeed.ui;

import com.slayerspeed.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskPreviewPanelTest
{
    @Test public void comparisonSnapshotCannotChangeRecordingOrHistory()
    {
        ActiveTask active = new ActiveTask("Araxytes", null, 100, 100, 1000);
        active.observeEncounter(new EncounterProfile("npc:araxyte", "Araxytes"));
        TaskStatistics history = new TaskStatistics("Araxytes", null, "boss:araxxor", "Araxxor");
        history.addRun(new TaskRun("boss-run", "Araxytes", null, "boss:araxxor", "Araxxor",
            20, 0, true, 20, 20, 20000, 0, 0, 3600000, 0, 1, 3600001, TaskRunStatus.COMPLETED), 50);
        TaskPreviewPanel.Option snapshot = new TaskPreviewPanel.Option(history);
        double eta = snapshot.estimate(10).getEtaMillis().getAsDouble();
        assertEquals(1800000, eta, 0.001);
        history.deleteRun("boss-run");
        assertEquals(eta, snapshot.estimate(10).getEtaMillis().getAsDouble(), 0.001);
        assertEquals("npc:araxyte", active.finish(TaskRunStatus.COMPLETED, 4000000).getEncounterProfileId());
        assertFalse(active.hasManualEncounterProfile());
        assertEquals(0, active.getTaskProgressUnits());
    }
}
