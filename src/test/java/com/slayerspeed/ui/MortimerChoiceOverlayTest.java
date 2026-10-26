package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedPlugin;
import com.slayerspeed.mortimer.MortimerEstimateService;
import com.slayerspeed.mortimer.MortimerTaskChoiceParser;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class MortimerChoiceOverlayTest
{
    @Test public void stoppedPluginDoesNotReadWidgetsEvenWhenEstimatesAreEnabled()
    {
        MortimerTaskChoiceParser parser = mock(MortimerTaskChoiceParser.class);
        MortimerChoiceOverlay overlay = new MortimerChoiceOverlay(new SlayerSpeedPlugin(),
            new SlayerSpeedConfig()
            {
                @Override public boolean experimentalMortimerEstimates() { return true; }
            }, parser, mock(MortimerEstimateService.class));
        Graphics2D graphics = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertNull(overlay.render(graphics)); }
        finally { graphics.dispose(); }
        verifyNoInteractions(parser);
    }
}
