package com.slayerspeed;

public enum SlayerSpeedOverlayMode
{
    FOLLOW_SIDEBAR("Follow sidebar"), SIMPLE("Compact"), DETAILED("Detailed");
    private final String label;
    SlayerSpeedOverlayMode(String label) { this.label = label; }
    public boolean isDetailed(SlayerSpeedDisplayMode sidebar)
    {
        return this == DETAILED || (this == FOLLOW_SIDEBAR && sidebar == SlayerSpeedDisplayMode.DETAILED);
    }
    @Override public String toString() { return label; }
}
