package com.LordDrakkon;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.Map;

/**
 * Draws lightweight respawn markers on tiles where you secured kills.
 * - Outline the tile
 * - Show a small countdown if we have a "best observed" respawn for that tile
 * - Uses only RuneLite types (no AWT Point), and no deprecated APIs
 */
public class MonsterRespawnOverlay extends Overlay
{
    private static final Color OUTLINE = new Color(120, 220, 255, 160);
    private static final Color TEXT_DUE = new Color(140, 255, 140);     // "respawn" / due now
    private static final Color TEXT_SOON = new Color(255, 230, 120);    // ticking down
    private static final Color TEXT_INFO = new Color(200, 220, 255);    // no PB yet

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
        // Quick guard: allow toggling from config (or default true if method missing)
        if (!plugin.isRespawnOverlayEnabled())
        {
            return null;
        }

        Map<WorldPoint, MonsterTrackerPlugin.RespawnInfo> data = plugin.respawnInfo();
        if (data == null || data.isEmpty())
        {
            return null;
        }

        final int plane = client.getPlane();
        final BasicStroke oldStroke = (BasicStroke) g.getStroke();
        g.setStroke(new BasicStroke(1f));

        long nowMs = System.currentTimeMillis();

        for (Map.Entry<WorldPoint, MonsterTrackerPlugin.RespawnInfo> e : data.entrySet())
        {
            WorldPoint wp = e.getKey();
            MonsterTrackerPlugin.RespawnInfo info = e.getValue();
            if (wp == null || info == null) continue;
            if (wp.getPlane() != plane) continue;

            // Localize tile (non-deprecated overload)
            LocalPoint lp = LocalPoint.fromWorld(client, wp.getX(), wp.getY());
            if (lp == null) continue;

            // Draw tile outline
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null)
            {
                OverlayUtil.renderPolygon(g, poly, OUTLINE);
            }

            // If we have a best observed respawn time, show a small countdown
            String text;
            Color color;
            if (info.bestSeconds > 0 && info.lastDeathMillis > 0)
            {
                long elapsed = Math.max(0L, (nowMs - info.lastDeathMillis) / 1000L);
                long remain = info.bestSeconds - elapsed;

                if (remain > 0)
                {
                    text = "⟳ " + remain + "s";
                    color = TEXT_SOON;
                }
                else
                {
                    text = "respawn";
                    color = TEXT_DUE;
                }
            }
            else
            {
                text = "mark";
                color = TEXT_INFO;
            }

            // Compute text location using RuneLite's Perspective (returns net.runelite.api.Point)
            Point textLoc = Perspective.getCanvasTextLocation(client, g, lp, text, 0);
            if (textLoc != null)
            {
                OverlayUtil.renderTextLocation(g, textLoc, text, color);
            }
        }

        g.setStroke(oldStroke);
        return null;
    }
}
