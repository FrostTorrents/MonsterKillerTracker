package com.LordDrakkon;

import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class MonsterTrackerOverlay extends OverlayPanel
{
    // Context-menu options (strings must match what the plugin checks)
    public static final String MENU_COPY           = "Copy stats to clipboard";
    public static final String MENU_PIN_XPH        = "Pin XP/hr";
    public static final String MENU_PIN_KPH        = "Pin Kills/hr";
    public static final String MENU_PIN_KL         = "Pin Kills Left";
    public static final String MENU_PIN_NL         = "Pin Next-Lvl Kills";
    public static final String MENU_UNPIN_ALL      = "Unpin all";
    // NEW: history actions
    public static final String MENU_OPEN_HISTORY   = "Open Slayer History";
    public static final String MENU_CLEAR_HISTORY  = "Clear Slayer History";

    private static final String BEAD = "●";

    private final MonsterTrackerPlugin plugin;
    private final MonsterTrackerConfig config;

    @Inject
    public MonsterTrackerOverlay(MonsterTrackerPlugin plugin, MonsterTrackerConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);

        panelComponent.setPreferredSize(new Dimension(Math.max(220, config.overlayWidth()), 0));
        panelComponent.setGap(new java.awt.Point(0, 5));
        panelComponent.setBorder(new java.awt.Rectangle(8, 8, 8, 8));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 180));

        // Right-click menu
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_COPY, null));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_PIN_XPH, null));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_PIN_KPH, null));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_PIN_KL, null));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_PIN_NL, null));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_UNPIN_ALL, null));
        // NEW: history items
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_OPEN_HISTORY, null));
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, MENU_CLEAR_HISTORY, null));
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        panelComponent.setPreferredSize(new Dimension(Math.max(220, config.overlayWidth()), 0));
        panelComponent.getChildren().clear();

        final String displayName = nz(plugin.getDisplayMonsterName());
        final String headerName  = displayName.isEmpty() ? "(no target)" : displayName;

        // ---------------- Header ----------------
        if (config.showHeader())
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Monster: " + headerName)
                    .color(Color.WHITE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Tracking:")
                    .right(plugin.getTrackedSkillName())
                    .build());

            final String killsLabel = displayName.isEmpty() ? "Kills:" : (displayName + " Kills:");
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(killsLabel)
                    .right(fmt(plugin.getKills()))
                    .build());
        }

        // ---------------- Leveling ----------------
        final boolean slayerOn   = config.slayerEnabled();
        final boolean onTaskNow  = slayerOn && plugin.isOnTaskNow();
        final boolean showLeveling = config.showLevelingBlock()
                && plugin.hasAnyData()
                && (!onTaskNow || config.slayerShowLevelingOnTask());

        if (showLeveling || (!slayerOn && config.showLevelingBlock()))
        {
            if (plugin.hasAnyData())
            {
                spacer();
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Leveling")
                        .color(new Color(180, 200, 255))
                        .build());

                if (config.showXpHourRow())
                {
                    String xpRight = String.format("%,d", plugin.getPinnedOrLiveXpPerHour());
                    if (config.showPaceArrows())
                    {
                        String a = plugin.getXpPaceArrow();
                        if (!a.isEmpty()) xpRight += " " + a;
                    }
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("XP/Hour:")
                            .right(xpRight)
                            .build());
                }

                if (config.showKillsHourRow())
                {
                    String kphRight = String.format("%,d", plugin.getPinnedOrLiveKillsPerHour());
                    if (config.showPaceArrows())
                    {
                        String ak = plugin.getKphPaceArrow();
                        if (!ak.isEmpty()) kphRight += " " + ak;
                    }
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Kills/Hour:")
                            .right(kphRight)
                            .build());
                }

                if (config.showKillsLeftRow())
                {
                    String kl = fmt(plugin.getPinnedOrLiveKillsLeft());
                    if (config.showConfidence()) kl += "  " + BEAD;
                    LineComponent.LineComponentBuilder klRow = LineComponent.builder()
                            .left("Kills Left:")
                            .right(kl);
                    if (config.showConfidence()) klRow.rightColor(plugin.getConfidenceColor());
                    panelComponent.getChildren().add(klRow.build());
                }

                if (config.showNextLevelRow())
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Next Level Kills:")
                            .right(fmt(plugin.getPinnedOrLiveKillsToNextLevel()))
                            .build());
                }

                if (config.showTimeLeftRow())
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Time Left:")
                            .right(formatTime(plugin.getTimeLeftHours()))
                            .build());
                }

                // GP/hr — separate from ROI (Slayer)
                if (config.showGpHrRow())
                {
                    int gphr = plugin.gpPerHourPicked();
                    if (gphr > 0)
                    {
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("GP/hr:")
                                .right(String.format("%,d", gphr))
                                .build());
                    }
                }
            }
        }

        // ---------------- Slayer Task (auto-hide when finished) ----------------
        if (plugin.shouldShowSlayerPanel() && config.showSlayerBlock())
        {
            spacer();
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Slayer Task")
                    .color(new Color(180, 255, 180))
                    .build());

            final String taskName   = plugin.getSlayerTaskName();
            final Integer left      = plugin.getSlayerRemaining();
            final int elapsedSecs   = plugin.getSlayerElapsedSeconds();
            final int taskKph       = (int) Math.round(plugin.getSlayerTaskKph());
            final String eta        = plugin.getSlayerTimeLeftHours() > 0 ? formatTime(plugin.getSlayerTimeLeftHours()) : "-";

            if (config.showTaskName())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Task:")
                        .right(taskName == null || taskName.isEmpty() ? "-" : taskName)
                        .build());
            }

            if (config.showTaskLeft())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Task Left:")
                        .right(left == null ? "-" : fmt(left))
                        .build());
            }

            if (config.showTaskTime())
            {
                String t = elapsedSecs == 0 ? "-" : formatSeconds(elapsedSecs);
                if (plugin.isSlayerPaused()) t += " (Paused)";
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Task Time:")
                        .right(t)
                        .build());
            }

            if (config.showTaskPace())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Task Pace:")
                        .right(taskKph > 0 ? String.format("%,d kph", taskKph) : "-")
                        .build());
            }

            if (config.showTaskEta())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Task ETA:")
                        .right(eta)
                        .build());
            }

            // ROI (no GP/hr here—kept separate in Leveling)
            if (config.slayerShowSkipRoi())
            {
                MonsterTrackerPlugin.SkipRoi roi = plugin.getSkipRoi();
                if (roi != null)
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Skip ROI:")
                            .right(roi.label)
                            .rightColor(roi.color == null ? Color.WHITE : roi.color)
                            .build());

                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(" ")
                            .right(roi.etaStr + " @ " + String.format("%,d kph", roi.kph))
                            .rightColor(roi.color == null ? Color.WHITE : roi.color)
                            .build());
                }
            }
        }

        // ---------------- Cannon ----------------
        if (plugin.isCannonEnabled() && config.showCannonBlock())
        {
            spacer();
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Cannon")
                    .color(new Color(255, 220, 150))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(plugin.isCannonActive() ? "Active" : "Idle")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Balls Used:")
                    .right(fmt(plugin.getCannonBallsLoaded()))
                    .build());

            int gpHr = plugin.getCannonGpPerHour();
            if (gpHr > 0)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Cannon GP/hr:")
                        .right(String.format("%,d", gpHr))
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Uptime:")
                    .right(formatSeconds(plugin.getCannonUptimeSeconds()))
                    .build());
        }

        return super.render(g);
    }

    // ---------- helpers ----------
    private static String nz(String s){ return s == null ? "" : s; }
    private static String fmt(int n){ return String.format("%,d", n); }
    private void spacer(){ panelComponent.getChildren().add(LineComponent.builder().left(" ").right(" ").build()); }
    private String formatTime(double hours){ return formatSeconds((int)Math.max(Math.round(hours * 3600.0), 0)); }
    private String formatSeconds(int t){ int h=t/3600,m=(t%3600)/60,s=t%60; return String.format("%02d:%02d:%02d", h, m, s); }
}
