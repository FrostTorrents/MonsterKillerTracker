package com.LordDrakkon;

import net.runelite.api.Skill;
import net.runelite.client.config.*;

@ConfigGroup("monstertracker")
public interface MonsterTrackerConfig extends Config
{
    // ───────────────────────── Sections ─────────────────────────
    @ConfigSection(
            name = "Display",
            description = "Overlay appearance and which rows to show",
            position = 0
    )
    String secDisplay = "display";

    @ConfigSection(
            name = "Slayer",
            description = "Task pace, ETA, pause, ROI",
            position = 1
    )
    String secSlayer = "slayer";

    @ConfigSection(
            name = "Cannon",
            description = "Cannon tracking",
            position = 2
    )
    String secCannon = "cannon";

    @ConfigSection(
            name = "Detection",
            description = "How the plugin detects your current target",
            position = 3
    )
    String secDetect = "detect";

    @ConfigSection(
            name = "Advanced",
            description = "Auto-cap XP/kill outlier filtering",
            position = 4,
            closedByDefault = true
    )
    String secAdvanced = "advanced";

    @ConfigSection(
            name = "Windows / Tools",
            description = "Open helper windows, clear history, etc.",
            position = 5
    )
    String secWindows = "windows";

    // ────────────────────── Display options ─────────────────────
    @Range(min = 180, max = 420)
    @ConfigItem(
            keyName = "overlayWidth",
            name = "Overlay width",
            description = "Set overlay width (px)",
            position = 0,
            section = secDisplay
    )
    default int overlayWidth() { return 240; }

    @ConfigItem(
            keyName = "showHeader",
            name = "Show header",
            description = "Show monster name, skill, total kills",
            position = 1,
            section = secDisplay
    )
    default boolean showHeader() { return true; }

    @ConfigItem(
            keyName = "showLevelingBlock",
            name = "Show Leveling block",
            description = "XP/hr, KPH, Kills Left, Next Level, Time Left, GP/hr",
            position = 2,
            section = secDisplay
    )
    default boolean showLevelingBlock() { return true; }

    @ConfigItem(
            keyName = "showXpHourRow",
            name = "• Show XP/Hour",
            description = "Row inside Leveling block",
            position = 3,
            section = secDisplay
    )
    default boolean showXpHourRow() { return true; }

    @ConfigItem(
            keyName = "showKillsHourRow",
            name = "• Show Kills/Hour",
            description = "Row inside Leveling block",
            position = 4,
            section = secDisplay
    )
    default boolean showKillsHourRow() { return true; }

    @ConfigItem(
            keyName = "showKillsLeftRow",
            name = "• Show Kills Left (goal)",
            description = "Row inside Leveling block",
            position = 5,
            section = secDisplay
    )
    default boolean showKillsLeftRow() { return true; }

    @ConfigItem(
            keyName = "showNextLevelRow",
            name = "• Show Next Level Kills",
            description = "Row inside Leveling block",
            position = 6,
            section = secDisplay
    )
    default boolean showNextLevelRow() { return true; }

    @ConfigItem(
            keyName = "showTimeLeftRow",
            name = "• Show Time Left",
            description = "Row inside Leveling block",
            position = 7,
            section = secDisplay
    )
    default boolean showTimeLeftRow() { return true; }

    @ConfigItem(
            keyName = "showGpHrRow",
            name = "• Show GP/hr (picked)",
            description = "Row inside Leveling block",
            position = 8,
            section = secDisplay
    )
    default boolean showGpHrRow() { return true; }

    @ConfigItem(
            keyName = "showPaceArrows",
            name = "Show pace arrows",
            description = "▲ improving, ▼ slowing, ▶ steady (last ~5 min vs session)",
            position = 9,
            section = secDisplay
    )
    default boolean showPaceArrows() { return true; }

    @ConfigItem(
            keyName = "showConfidence",
            name = "Show confidence bead",
            description = "Colors the Kills Left row based on XP/kill stability",
            position = 10,
            section = secDisplay
    )
    default boolean showConfidence() { return true; }

    // ─────────────────────── Level targets ──────────────────────
    enum TargetSkill
    {
        ATTACK(Skill.ATTACK),
        STRENGTH(Skill.STRENGTH),
        DEFENCE(Skill.DEFENCE),
        RANGED(Skill.RANGED),
        MAGIC(Skill.MAGIC),
        HITPOINTS(Skill.HITPOINTS),
        SLAYER(Skill.SLAYER);

        private final Skill s;
        TargetSkill(Skill s){ this.s = s; }
        public Skill toSkill(){ return s; }
    }

    @ConfigItem(
            keyName = "targetSkill",
            name = "Target skill",
            description = "Which skill to track XP and goals for",
            position = 20,
            section = secDisplay
    )
    default TargetSkill targetSkill() { return TargetSkill.STRENGTH; }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "targetLevel",
            name = "Target level",
            description = "Used for Kills Left (goal)",
            position = 21,
            section = secDisplay
    )
    default int targetLevel() { return 99; }

    // ───────────────────────── Slayer ───────────────────────────
    @ConfigItem(
            keyName = "slayerEnabled",
            name = "Enable Slayer tracking",
            description = "Import task from RL, pace/ETA, pause on idle",
            position = 0,
            section = secSlayer
    )
    default boolean slayerEnabled() { return true; }

    @ConfigItem(
            keyName = "showSlayerBlock",
            name = "Show Slayer block",
            description = "Shows Task, Left, Time, Pace, ETA, ROI",
            position = 1,
            section = secSlayer
    )
    default boolean showSlayerBlock() { return true; }

    @ConfigItem(
            keyName = "slayerShowLevelingOnTask",
            name = "Keep Leveling block on task",
            description = "Show leveling block while on-task",
            position = 2,
            section = secSlayer
    )
    default boolean slayerShowLevelingOnTask() { return true; }

    @ConfigItem(
            keyName = "showTaskName",
            name = "• Show Task name",
            description = "Row inside Slayer block",
            position = 3,
            section = secSlayer
    )
    default boolean showTaskName() { return true; }

    @ConfigItem(
            keyName = "showTaskLeft",
            name = "• Show Task Left",
            description = "Row inside Slayer block",
            position = 4,
            section = secSlayer
    )
    default boolean showTaskLeft() { return true; }

    @ConfigItem(
            keyName = "showTaskTime",
            name = "• Show Task Time (active)",
            description = "Pauses after idle threshold",
            position = 5,
            section = secSlayer
    )
    default boolean showTaskTime() { return true; }

    @ConfigItem(
            keyName = "showTaskPace",
            name = "• Show Task Pace (kph)",
            description = "Row inside Slayer block",
            position = 6,
            section = secSlayer
    )
    default boolean showTaskPace() { return true; }

    @ConfigItem(
            keyName = "showTaskEta",
            name = "• Show Task ETA",
            description = "Row inside Slayer block",
            position = 7,
            section = secSlayer
    )
    default boolean showTaskEta() { return true; }

    @ConfigItem(
            keyName = "slayerShowSkipRoi",
            name = "Show Skip ROI",
            description = "Compares your current KPH/GP/hr vs personal medians",
            position = 8,
            section = secSlayer
    )
    default boolean slayerShowSkipRoi() { return true; }

    @Range(min = 1, max = 30)
    @ConfigItem(
            keyName = "slayerPauseIdleMinutes",
            name = "Pause after idle (min)",
            description = "Idle threshold to pause task time",
            position = 9,
            section = secSlayer
    )
    default int slayerPauseIdleMinutes() { return 2; }

    @Range(min = 10, max = 90)
    @ConfigItem(
            keyName = "slayerRoiSensitivityPct",
            name = "ROI sensitivity (%)",
            description = "Lower = stricter (more likely to suggest skipping)",
            position = 10,
            section = secSlayer
    )
    default int slayerRoiSensitivityPct() { return 60; }

    // ───────────────────────── Cannon ───────────────────────────
    @ConfigItem(
            keyName = "cannonEnabled",
            name = "Enable cannon tracking",
            description = "Reads chat to detect setup/load/pickup",
            position = 0,
            section = secCannon
    )
    default boolean cannonEnabled() { return true; }

    @ConfigItem(
            keyName = "showCannonBlock",
            name = "Show Cannon block",
            description = "State, balls used, GP/hr, uptime",
            position = 1,
            section = secCannon
    )
    default boolean showCannonBlock() { return true; }

    // ─────────────────────── Detection ──────────────────────────
    @Range(min = 1, max = 6)
    @ConfigItem(
            keyName = "detectLatchTicks",
            name = "Latch after (ticks)",
            description = "Ticks of attacking a new NPC before switching target",
            position = 0,
            section = secDetect
    )
    default int detectLatchTicks() { return 1; }

    @Range(min = 1, max = 10)
    @ConfigItem(
            keyName = "detectLoseTicks",
            name = "Lose target after (ticks idle)",
            description = "Idle ticks before forgetting the current target",
            position = 1,
            section = secDetect
    )
    default int detectLoseTicks() { return 4; }

    // ─────────────────────── Advanced (XP cap) ──────────────────
    @ConfigItem(
            keyName = "autoCapXpPerKill",
            name = "Auto-cap XP/kill outliers",
            description = "MAD-based bounds to ignore odd kills (multi-hit, tag, etc.)",
            position = 0,
            section = secAdvanced
    )
    default boolean autoCapXpPerKill() { return true; }

    @Range(min = 1, max = 50000)
    @ConfigItem(
            keyName = "hardAbsoluteCap",
            name = "Hard absolute cap (XP/kill)",
            description = "Upper guardrail regardless of auto-cap",
            position = 1,
            section = secAdvanced
    )
    default int hardAbsoluteCap() { return 10000; }

    @Range(min = 1, max = 200)
    @ConfigItem(
            keyName = "autoCapMinKills",
            name = "Auto-cap min samples",
            description = "Minimum valid kills before confidence goes green",
            position = 2,
            section = secAdvanced
    )
    default int autoCapMinKills() { return 8; }

    @Range(min = 8, max = 256)
    @ConfigItem(
            keyName = "autoCapWindow",
            name = "Auto-cap window size",
            description = "Number of recent kills to model",
            position = 3,
            section = secAdvanced
    )
    default int autoCapWindow() { return 32; }

    @Range(min = 1, max = 5000)
    @ConfigItem(
            keyName = "minXpPerKillFloor",
            name = "Min XP/kill (floor)",
            description = "Ignore kills awarding less than this",
            position = 4,
            section = secAdvanced
    )
    default int minXpPerKillFloor() { return 5; }

    @Range(min = 10, max = 50000)
    @ConfigItem(
            keyName = "maxXpPerKill",
            name = "Max XP/kill (manual cap)",
            description = "Used if Auto-cap is off",
            position = 5,
            section = secAdvanced
    )
    default int maxXpPerKill() { return 4000; }

    // ───────────────────── Windows / Tools ──────────────────────
    @ConfigItem(
            keyName = "respawnOverlay",
            name = "Enable respawn overlay",
            description = "Shows best respawn timers at death tiles",
            position = 0,
            section = secWindows
    )
    default boolean respawnOverlay() { return true; }

    @ConfigItem(
            keyName = "openHistoryTrigger",
            name = "Open Slayer history",
            description = "Toggle this on to open the history window (auto-resets)",
            position = 1,
            section = secWindows
    )
    default boolean openHistoryTrigger() { return false; }

    @ConfigItem(
            keyName = "clearHistoryTrigger",
            name = "Clear Slayer history",
            description = "Toggle this on to clear saved history (auto-resets)",
            position = 2,
            section = secWindows
    )
    default boolean clearHistoryTrigger() { return false; }

    @ConfigItem(
            keyName = "openFaq",
            name = "Open FAQ window",
            description = "Toggle this on to open the FAQ (auto-resets)",
            position = 3,
            section = secWindows
    )
    default boolean openFaq() { return false; }
}
