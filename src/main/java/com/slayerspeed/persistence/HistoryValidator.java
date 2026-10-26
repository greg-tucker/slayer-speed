package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slayerspeed.model.SlayerSpeedData;
import com.slayerspeed.model.ActiveTask;
import java.math.BigDecimal;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskStatistics;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Reject unsafe data before it reaches mutable state or calculations. */
final class HistoryValidator
{
    static final int MAX_CHARACTERS = 8 * 1024 * 1024;

    // Serialized schema fields are explicit so validation needs no reflective access.
    // Keep these lists aligned with SlayerSpeedData, TaskStatistics, TaskRun and ActiveTask.
    private static final Set<String> INTEGER_FIELDS = Set.of(
        "schemaVersion", "timingPolicy", "rateUnits", "rateKills", "rateXp",
        "measuredUnits", "measuredKills", "measuredXp", "initialAmount", "observedStartAmount",
        "lastRemainingAmount", "endingAmount", "actualKills", "taskProgressUnits", "killSlayerXp",
        "bonusSlayerXp", "cannonballsUsed", "totalActualKills", "totalTaskProgressUnits", "totalSlayerXp",
        "totalCannonballsUsed", "totalCannonRunActualKills", "totalCannonRunTaskProgressUnits", "completedTaskCount");
    private static final Set<String> LONG_FIELDS = Set.of(
        "segmentAnchorMillis", "activeMillis", "setupMillis", "startedAtMillis", "lastActivityAtMillis",
        "lastCheckpointAtMillis", "completionConfirmedAtMillis", "completedAtMillis", "totalActiveMillis",
        "totalCompletedTaskMillis", "lastUpdatedAtMillis");
    private static final String DECIMAL_FIELD = "personalBestEffectiveKph";
    private HistoryValidator() {}

    static SlayerSpeedData decode(String json, Gson gson)
    {
        if (json == null || json.length() > MAX_CHARACTERS)
        {
            throw new IllegalArgumentException("History exceeds the 8 MiB text limit");
        }
        JsonElement parsed = new JsonParser().parse(json);
        if (!parsed.isJsonObject()) { throw new IllegalArgumentException("Expected a history object"); }
        JsonObject object = parsed.getAsJsonObject();
        if (!object.has("schemaVersion") || !object.get("schemaVersion").isJsonPrimitive())
        {
            throw new IllegalArgumentException("Missing history schema");
        }
        validateNumbers(parsed);
        int version = object.get("schemaVersion").getAsInt();
        if (version < 1 || version > SlayerSpeedData.CURRENT_SCHEMA_VERSION
            || object.get("schemaVersion").getAsDouble() != version)
        {
            throw new IllegalArgumentException("Unsupported history schema");
        }
        SlayerSpeedData data = gson.fromJson(object, SlayerSpeedData.class);
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, TaskStatistics> entry : data.getStatisticsByTaskKey().entrySet())
        {
            TaskStatistics stats = entry.getValue();
            if (stats == null || stats.getTaskName() == null || stats.getTaskName().trim().isEmpty())
            {
                throw new IllegalArgumentException("Missing task name");
            }
            if (stats.getTimingPolicy() < 0 || stats.getTimingPolicy() > 1) { throw new IllegalArgumentException("Unknown timing policy"); }
            validateText(stats.getTaskName());
            validateText(stats.getTaskLocation());
            validateText(stats.getEncounterProfileName());
            if (version >= 5 && !entry.getKey().equals(new com.slayerspeed.model.TaskKey(
                stats.getTaskName(), stats.getTaskLocation(), stats.getEncounterProfileId(), stats.getTimingPolicy()).asStorageKey()))
            {
                throw new IllegalArgumentException("Task key does not match stored profile");
            }
            for (TaskRun run : stats.getRecentRuns())
            {
                if (run == null || run.getId() == null || run.getId().isEmpty()
                    || !ids.add(run.getId()) || run.getStatus() == null
                    || run.getTaskName() == null || run.getTaskName().trim().isEmpty())
                {
                    throw new IllegalArgumentException("Invalid or duplicate task run");
                }
                if (run.getTimingPolicy() != stats.getTimingPolicy()) { throw new IllegalArgumentException("Mixed timing policy"); }
                if (run.getRateTaskProgressUnits() > run.getTaskProgressUnits()
                    || run.getRateActualKills() > run.getActualKills()
                    || (long) run.getKillSlayerXp() + run.getBonusSlayerXp() > Integer.MAX_VALUE
                    || run.getRateSlayerXp() > run.getTotalSlayerXp())
                {
                    throw new IllegalArgumentException("Rate sample exceeds observed counters");
                }
                validateText(run.getTaskName());
                validateText(run.getTaskLocation());
                validateText(run.getEncounterProfileName());
                if (version >= 5 && !run.taskKey(true).asStorageKey().equals(entry.getKey()))
                {
                    throw new IllegalArgumentException("Run belongs to another task profile");
                }
                if (run.getCompletedAtMillis() < run.getStartedAtMillis())
                {
                    throw new IllegalArgumentException("Invalid run timestamps");
                }
            }
        }
        ActiveTask active = data.getCheckpointedActiveTask();
        if (active != null)
        {
            if (active.getTaskName() == null || active.getTaskName().trim().isEmpty()
                || active.getId() == null || active.getId().isEmpty() || ids.contains(active.getId())
                || active.getTimingPolicy() < 0 || active.getTimingPolicy() > 1
                || active.getRateTaskProgressUnits() > active.getTaskProgressUnits()
                || active.getRateActualKills() > active.getActualKills()
                || (long) active.getKillSlayerXp() + active.getBonusSlayerXp() > Integer.MAX_VALUE
                || active.getRateSlayerXp() > active.getTotalSlayerXp())
            {
                throw new IllegalArgumentException("Invalid active task");
            }
            validateText(active.getTaskName());
            validateText(active.getTaskLocation());
            validateText(active.getManualEncounterProfileName());
            validateText(active.getDetectedEncounterProfileName());
        }
        return data;
    }

    private static void validateText(String text)
    {
        if (text != null && (text.length() > 300 || text.indexOf('<') >= 0 || text.indexOf('>') >= 0))
        {
            throw new IllegalArgumentException("Invalid task display text");
        }
    }

    private static void validateNumbers(JsonElement element)
    {
        if (element.isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
            {
                JsonElement value = entry.getValue();
                boolean integerField = INTEGER_FIELDS.contains(entry.getKey());
                boolean longField = LONG_FIELDS.contains(entry.getKey());
                boolean decimalField = DECIMAL_FIELD.equals(entry.getKey());
                if ((integerField || longField || decimalField)
                    && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()))
                {
                    throw new IllegalArgumentException("Numeric fields must contain JSON numbers");
                }
                if (integerField || longField)
                {
                    BigDecimal exact = value.getAsBigDecimal();
                    BigDecimal maximum = BigDecimal.valueOf(longField ? Long.MAX_VALUE : Integer.MAX_VALUE);
                    if (exact.signum() < 0 || exact.stripTrailingZeros().scale() > 0 || exact.compareTo(maximum) > 0)
                    {
                        throw new IllegalArgumentException("Numeric value is outside its storage range");
                    }
                }
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
                {
                    double number = value.getAsDouble();
                    if (!Double.isFinite(number) || number < 0
                        || (!decimalField && number != Math.rint(number))
                        || (entry.getKey().endsWith("Millis") && number > Long.MAX_VALUE)
                        || (!entry.getKey().endsWith("Millis") && !decimalField
                            && number > Integer.MAX_VALUE))
                    {
                        throw new IllegalArgumentException("Invalid numeric history value");
                    }
                }
                validateNumbers(value);
            }
        }
        else if (element.isJsonArray())
        {
            for (JsonElement child : element.getAsJsonArray()) { validateNumbers(child); }
        }
    }
}
