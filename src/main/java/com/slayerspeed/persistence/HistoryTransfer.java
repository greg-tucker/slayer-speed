package com.slayerspeed.persistence;

import com.google.gson.*;
import com.slayerspeed.model.*;
import java.util.*;

/** Pure codec/merge operations; no account or file access. */
final class HistoryTransfer
{
    private HistoryTransfer() {}

    static String exportData(SlayerSpeedData data, Gson gson, boolean checkpoint)
    {
        SlayerSpeedData copy = gson.fromJson(gson.toJson(data), SlayerSpeedData.class);
        if (!checkpoint) { copy.setCheckpointedActiveTask(null); }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("format", "slayer-task-speed");
        envelope.addProperty("version", 1);
        envelope.addProperty("containsCheckpoint", checkpoint && copy.getCheckpointedActiveTask() != null);
        envelope.add("history", gson.toJsonTree(copy));
        String text = gson.toJson(envelope);
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > HistoryValidator.MAX_CHARACTERS)
        {
            throw new IllegalArgumentException("Export exceeds the supported size limit");
        }
        return text;
    }

    static SlayerSpeedData parse(String text, Gson gson)
    {
        if (text == null || text.length() > HistoryValidator.MAX_CHARACTERS)
        {
            throw new IllegalArgumentException("Import exceeds the 8 MiB text limit");
        }
        JsonElement parsed = new JsonParser().parse(text);
        if (!parsed.isJsonObject()) { throw new IllegalArgumentException("Expected a history file"); }
        JsonObject root = parsed.getAsJsonObject();
        if (root.has("format"))
        {
            if (!"slayer-task-speed".equals(root.get("format").getAsString())
                || !root.has("version") || !root.get("version").isJsonPrimitive()
                || !root.get("version").getAsJsonPrimitive().isNumber()
                || root.get("version").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0 || !root.has("history"))
            {
                throw new IllegalArgumentException("Unsupported export format");
            }
            text = gson.toJson(root.get("history"));
        }
        SlayerSpeedData data = HistoryValidator.decode(text, gson);
        if (data.getSchemaVersion() != SlayerSpeedData.CURRENT_SCHEMA_VERSION && !data.migrateToCurrentSchema())
        {
            throw new IllegalArgumentException("Unsupported history schema");
        }
        return data;
    }

    static SlayerSpeedData merge(SlayerSpeedData existing, SlayerSpeedData incoming, Gson gson, int limit)
    {
        Map<String, TaskRun> runs = new LinkedHashMap<>();
        for (SlayerSpeedData data : Arrays.asList(existing, incoming))
        {
            for (TaskStatistics stats : data.getStatisticsByTaskKey().values())
            {
                TaskStatistics reconstructed = new TaskStatistics(stats.getTaskName(), stats.getTaskLocation(),
                    stats.getEncounterProfileId(), stats.getEncounterProfileName(), stats.getTimingPolicy());
                for (TaskRun run : stats.getRecentRuns()) { reconstructed.addRun(run, Integer.MAX_VALUE); }
                if (!sameTotals(stats, reconstructed))
                {
                    throw new IllegalArgumentException("Older unretained totals cannot be safely merged. Use Replace after exporting a backup.");
                }
                for (TaskRun run : stats.getRecentRuns())
                {
                    TaskRun previous = runs.putIfAbsent(run.getId(), run);
                    if (previous != null && !gson.toJson(previous).equals(gson.toJson(run)))
                    {
                        throw new IllegalArgumentException("Conflicting run ID. Resolve with a reviewed Replace import.");
                    }
                }
            }
        }
        SlayerSpeedData merged = new SlayerSpeedData();
        List<TaskRun> ordered = new ArrayList<>(runs.values());
        ordered.sort(Comparator.comparingLong(TaskRun::getCompletedAtMillis).thenComparing(TaskRun::getId));
        for (TaskRun run : ordered)
        {
            String key = run.taskKey(true).asStorageKey();
            TaskStatistics stats = merged.getStatisticsByTaskKey().computeIfAbsent(key, ignored ->
                new TaskStatistics(run.getTaskName(), run.getTaskLocation(),
                    run.getEncounterProfileId(), run.getEncounterProfileName(), run.getTimingPolicy()));
            stats.addRun(run, limit);
        }
        return merged;
    }

    private static boolean sameTotals(TaskStatistics a, TaskStatistics b)
    {
        return a.getTotalActualKills() == b.getTotalActualKills()
            && a.getTotalTaskProgressUnits() == b.getTotalTaskProgressUnits()
            && a.getTotalSlayerXp() == b.getTotalSlayerXp()
            && a.getTotalCannonballsUsed() == b.getTotalCannonballsUsed()
            && a.getTotalCannonRunActualKills() == b.getTotalCannonRunActualKills()
            && a.getTotalCannonRunTaskProgressUnits() == b.getTotalCannonRunTaskProgressUnits()
            && a.getTotalActiveMillis() == b.getTotalActiveMillis()
            && a.getTotalCompletedTaskMillis() == b.getTotalCompletedTaskMillis()
            && a.getCompletedTaskCount() == b.getCompletedTaskCount();
    }
}
