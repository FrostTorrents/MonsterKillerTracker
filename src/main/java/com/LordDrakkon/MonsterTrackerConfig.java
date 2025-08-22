package com.LordDrakkon;

import net.runelite.api.Skill;
import net.runelite.client.config.*;

@ConfigGroup("monstertracker")
public interface MonsterTrackerConfig extends Config
{
    // ===== Skill choice (for goal calculations) =====
    enum TargetSkill
    {
        ATTACK(Skill.ATTACK),
        STRENGTH(Skill.STRENGTH),
        DEFENCE(Skill.DEFENCE),
        RANGED(Skill.RANGED),
        MAGIC(Skill.MAGIC),
        SLAYER(Skill.SLAYER);

        private final Skill skill;
        TargetSkill(Skill s) { this.skill = s; }
        public Skill toSkill() { return skill; }
        @Override public String toString() { return skill.getName(); }
    }

    // ---------------------------------------------------------------------
    // CORE
    // ---------------------------------------------------------------------
    @ConfigItem(
            keyName = "targetSkill",
            name = "Target skill",
            description = "Which combat skill your goal applies to",
            position = 0
    )
    default TargetSkill targetSkill() { return TargetSkill.ATTACK; }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "targetLevel",
            name = "Target level",
            description = "Level you want to reach",
            position = 1
    )
    default int targetLevel() { return 84; }

    // ---------------------------------------------------------------------
    // DISPLAY – let users decide exactly what appears on the overlay
    // ---------------------------------------------------------------------
    @ConfigSection(
            name = "Display",
            description = "Toggle individual rows/blocks in the HUD",
            position = 4
    )
    String display = "display";

    @Range(min = 200, max = 360)
    @ConfigItem(
            keyName = "overlayWidth",
            name = "Overlay width",
            description = "Panel width (px); widen to avoid wrapping",
            section = display,
            position = 5
    )
    default int overlayWidth() { return 260; }

    @ConfigItem(
            keyName = "showHeader",
            name = "Header (Monster / Tracking / Kills)",
            description = "Show the header block at the top",
            section = display,
            position = 6
    )
    default boolean showHeader() { return true; }

    @ConfigItem(
            keyName = "showLevelingBlock",
            name = "Leveling block",
            description = "Show XP/hr, KPH, Kills Left, etc.",
            section = display,
            position = 7
    )
    default boolean showLevelingBlock() { return true; }

    @ConfigItem(
            keyName = "showXpHourRow",
            name = "XP/Hour row",
            description = "Show XP/hr",
            section = display,
            position = 8
    )
    default boolean showXpHourRow() { return true; }

    @ConfigItem(
            keyName = "showKillsHourRow",
            name = "Kills/Hour row",
            description = "Show KPH",
            section = display,
            position = 9
    )
    default boolean showKillsHourRow() { return true; }

    @ConfigItem(
            keyName = "showPaceArrows",
            name = "Pace arrows (▲ ▶ ▼)",
            description = "Compare last ~5 min vs session average",
            section = display,
            position = 10
    )
    default boolean showPaceArrows() { return true; }

    @ConfigItem(
            keyName = "showKillsLeftRow",
            name = "Kills Left row",
            description = "Show Kills Left (goal to target level)",
            section = display,
            position = 11
    )
    default boolean showKillsLeftRow() { return true; }

    @ConfigItem(
            keyName = "showNextLevelRow",
            name = "Next Level Kills row",
            description = "Kills remaining to your NEXT level",
            section = display,
            position = 12
    )
    default boolean showNextLevelRow() { return true; }

    @ConfigItem(
            keyName = "showTimeLeftRow",
            name = "Time Left row",
            description = "Show estimated time to hit your target level",
            section = display,
            position = 13
    )
    default boolean showTimeLeftRow() { return true; }

    @ConfigItem(
            keyName = "showGpHrRow",
            name = "GP/hr row",
            description = "Show GP picked per hour (separate from ROI)",
            section = display,
            position = 14
    )
    default boolean showGpHrRow() { return true; }

    @ConfigItem(
            keyName = "showSlayerBlock",
            name = "Slayer Task block",
            description = "Show Slayer section (it hides automatically when task finishes)",
            section = display,
            position = 15
    )
    default boolean showSlayerBlock() { return true; }

    @ConfigItem(
            keyName = "showTaskName",
            name = "Task: name",
            description = "Show task name",
            section = display,
            position = 16
    )
    default boolean showTaskName() { return true; }

    @ConfigItem(
            keyName = "showTaskLeft",
            name = "Task: left",
            description = "Show remaining monsters on task",
            section = display,
            position = 17
    )
    default boolean showTaskLeft() { return true; }

    @ConfigItem(
            keyName = "showTaskTime",
            name = "Task: time",
            description = "Show task time (active time; pauses on idle)",
            section = display,
            position = 18
    )
    default boolean showTaskTime() { return true; }

    @ConfigItem(
            keyName = "showTaskPace",
            name = "Task: pace (KPH)",
            description = "Show task kills per hour",
            section = display,
            position = 19
    )
    default boolean showTaskPace() { return true; }

    @ConfigItem(
            keyName = "showTaskEta",
            name = "Task: ETA",
            description = "Show task ETA",
            section = display,
            position = 20
    )
    default boolean showTaskEta() { return true; }

    @ConfigItem(
            keyName = "showCannonBlock",
            name = "Cannon block",
            description = "Show cannon section (requires 'Enable cannon tracking' below)",
            section = display,
            position = 21
    )
    default boolean showCannonBlock() { return true; }

    // ---------------------------------------------------------------------
    // ENHANCEMENTS (visual/analytics)
    // ---------------------------------------------------------------------
    @ConfigSection(
            name = "Enhancements",
            description = "Extra tracking and visuals",
            position = 22,
            closedByDefault = true
    )
    String enh = "enh";

    @ConfigItem(
            keyName = "showConfidence",
            name = "Show confidence bead",
            description = "Colored bead beside Kills Left based on sample size & variance",
            section = enh,
            position = 1
    )
    default boolean showConfidence() { return true; }

    @ConfigItem(
            keyName = "respawnOverlay",
            name = "Respawn clock/route",
            description = "Draw lightweight respawn timers on tiles you farm",
            section = enh,
            position = 2
    )
    default boolean respawnOverlay() { return false; }

    @ConfigItem(
            keyName = "cannonEnabled",
            name = "Enable cannon tracking",
            description = "Track balls loaded, cannon GP/hr and uptime",
            section = enh,
            position = 3
    )
    default boolean cannonEnabled() { return true; }

    // ---------------------------------------------------------------------
    // SLAYER (pause, ROI, visibility while on-task)
    // ---------------------------------------------------------------------
    @ConfigSection(
            name = "Slayer",
            description = "Task tracking & ROI",
            position = 40,
            closedByDefault = true
    )
    String slayer = "slayer";

    @ConfigItem(
            keyName = "slayerEnabled",
            name = "Enable Slayer task",
            description = "Show task name, remaining, and timer/ETA",
            section = slayer,
            position = 1
    )
    default boolean slayerEnabled() { return true; }

    @ConfigItem(
            keyName = "slayerShowLevelingOnTask",
            name = "Show leveling on task",
            description = "Keep showing Kills Left / Time Left while on a Slayer task",
            section = slayer,
            position = 2
    )
    default boolean slayerShowLevelingOnTask() { return false; }

    @Range(min = 1, max = 15)
    @ConfigItem(
            keyName = "slayerPauseIdleMinutes",
            name = "Pause task after idle (min)",
            description = "Task timer pauses if idle this long; resumes when you attack again",
            section = slayer,
            position = 3
    )
    default int slayerPauseIdleMinutes() { return 3; }

    @ConfigItem(
            keyName = "slayerShowSkipRoi",
            name = "Show Skip ROI",
            description = "Show Keep / Consider Skip with ETA",
            section = slayer,
            position = 4
    )
    default boolean slayerShowSkipRoi() { return true; }

    @Range(min = 10, max = 90)
    @ConfigItem(
            keyName = "slayerRoiSensitivityPct",
            name = "Skip ROI sensitivity (%)",
            description = "Lower = recommend skipping sooner (compares to your personal medians)",
            section = slayer,
            position = 5
    )
    default int slayerRoiSensitivityPct() { return 75; }

    @ConfigItem(
            keyName = "slayerShowRoiGp",
            name = "Show ROI GP/hr line",
            description = "Append GP/hr to ROI line",
            section = slayer,
            position = 6
    )
    default boolean slayerShowRoiGp() { return false; }

    // ---------------------------------------------------------------------
    // TUNING (XP/kill filtering, target detection)
    // ---------------------------------------------------------------------
    @ConfigSection(
            name = "Tuning",
            description = "Optional tweaks for power users",
            position = 60,
            closedByDefault = true
    )
    String tuning = "tuning";

    @Range(min = 3, max = 100)
    @ConfigItem(
            keyName = "minKillsForStats",
            name = "Min kills for stats",
            description = "Kills required before showing XP/hr & kills left",
            section = tuning,
            position = 1
    )
    default int minKillsForStats() { return 10; }

    @ConfigItem(
            keyName = "autoCap",
            name = "Auto cap XP/kill",
            description = "Learn a per-NPC XP/kill cap from recent kills",
            section = tuning,
            position = 2
    )
    default boolean autoCapXpPerKill() { return true; }

    @Range(min = 10, max = 500)
    @ConfigItem(
            keyName = "autoCapWindow",
            name = "Auto cap window",
            description = "How many recent kills to learn from",
            section = tuning,
            position = 3
    )
    default int autoCapWindow() { return 100; }

    @Range(min = 5, max = 100)
    @ConfigItem(
            keyName = "autoCapMinKills",
            name = "Auto cap warmup",
            description = "Min valid kills before using the learned cap",
            section = tuning,
            position = 4
    )
    default int autoCapMinKills() { return 20; }

    @Range(min = 1, max = 2_000)
    @ConfigItem(
            keyName = "minXpPerKillFloor",
            name = "Manual min XP/kill (floor)",
            description = "Ignore kills awarding less than this XP when averaging",
            section = tuning,
            position = 5
    )
    default int minXpPerKillFloor() { return 8; }

    @Range(min = 100, max = 100_000)
    @ConfigItem(
            keyName = "maxXpPerKill",
            name = "Manual max XP/kill (cap)",
            description = "Used when Auto cap is OFF or during warmup",
            section = tuning,
            position = 6
    )
    default int maxXpPerKill() { return 2_000; }

    @Range(min = 500, max = 200_000)
    @ConfigItem(
            keyName = "hardAbsoluteCap",
            name = "Absolute XP/kill ceiling",
            description = "Never count a single kill above this (filters lamps/quests)",
            section = tuning,
            position = 7
    )
    default int hardAbsoluteCap() { return 5_000; }

    @Range(min = 1, max = 10)
    @ConfigItem(
            keyName = "detectLatchTicks",
            name = "Latch after (ticks)",
            description = "Consecutive ticks attacking the same NPC before locking onto it",
            section = tuning,
            position = 8
    )
    default int detectLatchTicks() { return 2; }

    @Range(min = 2, max = 20)
    @ConfigItem(
            keyName = "detectLoseTicks",
            name = "Lose target after (ticks)",
            description = "Ticks without interacting before the detected target is cleared",
            section = tuning,
            position = 9
    )
    default int detectLoseTicks() { return 8; }

    // ---------------------------------------------------------------------
    // RECORDS / WINDOWS
    // ---------------------------------------------------------------------
    @ConfigSection(
            name = "Recorded times",
            description = "Saved Slayer task durations (view/clear)",
            position = 80,
            closedByDefault = true
    )
    String records = "records";

    @ConfigItem(
            keyName = "openHistoryTrigger",
            name = "Open history now",
            description = "Opens a read-only window with recorded task times",
            section = records,
            position = 1
    )
    default boolean openHistoryTrigger() { return false; }

    @ConfigItem(
            keyName = "clearHistoryTrigger",
            name = "Clear history",
            description = "Deletes all recorded task times",
            section = records,
            position = 2
    )
    default boolean clearHistoryTrigger() { return false; }

    // ---------------------------------------------------------------------
    // FAQ / HELP
    // ---------------------------------------------------------------------
    @ConfigSection(
            name = "FAQ & Help",
            description = "Quick guide to what each stat means",
            position = 95,
            closedByDefault = true
    )
    String faq = "faq";

    @ConfigItem(
            keyName = "openFaq",
            name = "Open FAQ",
            description = "Shows a quick in-client FAQ",
            section = faq,
            position = 1
    )
    default boolean openFaq() { return false; }
}
