package com.LordDrakkon;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Map;

/**
 * Draws lightweight respawn clocks/markers on tiles where you recently killed an NPC.
 * - Outline the tile.
 * - Show "R: <secs>" when we can estimate remaining time to respawn.
 * - Else show "best: <secs>" for best observed respawn time.
 *
 * No reflection. Uses net.runelite.api.Point for text rendering.
 */
public class MonsterRespawnOverlay extends Overlay
{
    private static final Color TILE_COLOR = new Color(255, 230, 120, 120); // soft amber
    private static final Color TEXT_COLOR = Color.WHITE;

    private final Client client;
    private final MonsterTrackerPlugin plugin;
    private final MonsterTrackerConfig config;

    @Inject
    public MonsterRespawnOverlay(Client client, MonsterTrackerPlugin plugin, MonsterTrackerConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        // Respect toggle directly from config (no need to call back into plugin)
        if (!config.respawnOverlay())
        {
            return null;
        }

        Map<WorldPoint, MonsterTrackerPlugin.RespawnInfo> map = plugin.respawnInfo();
        if (map == null || map.isEmpty())
        {
            return null;
        }

        long now = System.currentTimeMillis();

        g.setStroke(new BasicStroke(1f));
        g.setColor(TILE_COLOR);

        for (Map.Entry<WorldPoint, MonsterTrackerPlugin.RespawnInfo> e : map.entrySet())
        {
            WorldPoint wp = e.getKey();
            MonsterTrackerPlugin.RespawnInfo ri = e.getValue();
            if (wp == null || ri == null)
            {
                continue;
            }

            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null)
            {
                continue;
            }

            // Draw tile polygon outline
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null)
            {
                g.draw(poly);
            }

            // Build label:
            // If we have a recent death + bestSeconds, show remaining.
            // Otherwise fallback to best observed.
            String label = null;
            if (ri.bestSeconds > 0 && ri.lastDeathMillis > 0)
            {
                int elapsed = (int) Math.max(0, (now - ri.lastDeathMillis) / 1000L);
                int remain = Math.max(0, ri.bestSeconds - elapsed);
                if (remain > 0)
                {
                    label = "R: " + remain + "s";
                }
                else
                {
                    label = "best: " + ri.bestSeconds + "s";
                }
            }
            else if (ri.bestSeconds > 0)
            {
                label = "best: " + ri.bestSeconds + "s";
            }

            if (label != null && !label.isEmpty())
            {
                Point textLoc = Perspective.getCanvasTextLocation(client, g, lp, label, 0);
                if (textLoc != null)
                {
                    OverlayUtil.renderTextLocation(g, textLoc, label, TEXT_COLOR);
                }
            }
        }

        return null;
    }
}
