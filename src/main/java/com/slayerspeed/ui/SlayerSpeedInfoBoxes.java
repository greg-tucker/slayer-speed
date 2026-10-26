package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedPlugin;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@Singleton
public final class SlayerSpeedInfoBoxes
{
    private final InfoBoxManager manager;
    private final SlayerSpeedPlugin plugin;
    private final SlayerSpeedConfig config;
    private final Map<SlayerSpeedInfoBox.Metric, SlayerSpeedInfoBox> boxes = new EnumMap<>(SlayerSpeedInfoBox.Metric.class);
    private final Set<SlayerSpeedInfoBox.Metric> registered = new HashSet<>();

    @Inject public SlayerSpeedInfoBoxes(InfoBoxManager manager, SlayerSpeedPlugin plugin, SlayerSpeedConfig config)
    {
        this.manager = manager;
        this.plugin = plugin;
        this.config = config;
        for (SlayerSpeedInfoBox.Metric metric : SlayerSpeedInfoBox.Metric.values())
        {
            boxes.put(metric, new SlayerSpeedInfoBox(plugin, config, metric));
        }
    }

    // Shutdown runs on the EDT; updates run on the client thread. Serialize registration.
    public synchronized void update()
    {
        for (Map.Entry<SlayerSpeedInfoBox.Metric, SlayerSpeedInfoBox> entry : boxes.entrySet())
        {
            boolean selected = plugin.isRunning() && config.showInfoBoxes() && entry.getValue().isSelected();
            if (selected && registered.add(entry.getKey())) { manager.addInfoBox(entry.getValue()); }
            else if (!selected && registered.remove(entry.getKey())) { manager.removeInfoBox(entry.getValue()); }
        }
    }

    public synchronized void clear()
    {
        for (SlayerSpeedInfoBox.Metric metric : registered) { manager.removeInfoBox(boxes.get(metric)); }
        registered.clear();
    }
}
