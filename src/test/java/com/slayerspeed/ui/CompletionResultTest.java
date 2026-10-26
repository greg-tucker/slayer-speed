package com.slayerspeed.ui;
import com.slayerspeed.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompletionResultTest
{
    @Test public void firstResultIsNotAdvertisedAsAnImprovementAndUnsavedIsExplicit()
    {
        ActiveTask task = new ActiveTask("Gargoyles", null, 100, 100, 1000);
        task.addProgressUnits(100);
        task.addActiveMillis(3600000);
        TaskRun run = task.finish(TaskRunStatus.COMPLETED, 4000000);
        CompletionResult result = new CompletionResult(run, false, false, true);
        assertTrue(result.getText().contains("First full task"));
        assertFalse(result.getText().contains("New PB"));
        assertTrue(result.getText().contains("Not saved"));
        assertTrue(result.getText().contains("Legacy timing"));
        assertEquals(run.getId(), result.getRunId());
        assertTrue(result.getHtml().contains("<br>"));
    }
}
