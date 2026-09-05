package com.slayerspeed.calculation;

import java.util.OptionalDouble;

/** Immutable numeric result and the evidence behind it. */
public final class TaskEstimate
{
    public enum Source { UNAVAILABLE, LIVE, HISTORY, BLENDED }
    private final Source source;
    private final OptionalDouble rate;
    private final OptionalDouble etaMillis;
    private final String scope;
    private final int fullTasks;
    private final String reason;

    TaskEstimate(Source source, OptionalDouble rate, OptionalDouble etaMillis,
        String scope, int fullTasks, String reason)
    {
        this.source = source;
        this.rate = rate;
        this.etaMillis = etaMillis;
        this.scope = scope;
        this.fullTasks = fullTasks;
        this.reason = reason;
    }

    public Source getSource() { return source; }
    public OptionalDouble getRate() { return rate; }
    public OptionalDouble getEtaMillis() { return etaMillis; }
    public String getScope() { return scope; }
    public int getFullTasks() { return fullTasks; }
    public boolean includesLive() { return source == Source.LIVE || source == Source.BLENDED; }

    public String getDescription()
    {
        switch (source)
        {
            case LIVE: return "Early estimate — this task only";
            case HISTORY: return scope;
            case BLENDED: return scope + " + this task";
            default: return reason;
        }
    }

    public String getSampleDescription()
    {
        return fullTasks + " fully observed tasks. Total lifetime rate-sample count unavailable.";
    }
}
