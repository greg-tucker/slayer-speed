package com.slayerspeed;

public enum SlayerSpeedInGameDisplay
{
    OVERLAY("Overlay", true, false),
    INFOBOXES("Infoboxes", false, true),
    BOTH("Both", true, true),
    OFF("Off", false, false);

    private final String label;
    private final boolean overlay;
    private final boolean infoBoxes;

    SlayerSpeedInGameDisplay(String label, boolean overlay, boolean infoBoxes)
    {
        this.label = label;
        this.overlay = overlay;
        this.infoBoxes = infoBoxes;
    }

    public boolean showsOverlay() { return overlay; }
    public boolean showsInfoBoxes() { return infoBoxes; }
    @Override public String toString() { return label; }
}
