package com.slayerspeed.calculation;

/** Recent windows are opt-in and only use eligible retained runs. */
public enum EstimateWindow
{
    LIFETIME(0, "Lifetime"),
    RECENT_5(5, "Recent 5"),
    RECENT_10(10, "Recent 10"),
    RECENT_25(25, "Recent 25");

    private final int size;
    private final String label;
    EstimateWindow(int size, String label) { this.size = size; this.label = label; }
    public int getSize() { return size; }
    @Override public String toString() { return label; }
}
