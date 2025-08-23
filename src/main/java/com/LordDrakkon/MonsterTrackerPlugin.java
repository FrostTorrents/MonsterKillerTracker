package com.LordDrakkon;

import com.google.inject.Provides;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.*;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Combat Progress HUD – core plugin (no reflection). */
@SuppressWarnings({"deprecation", "unused"})
@PluginDescriptor(
        name = "Combat Progress HUD",
        description = "Tracks combat XP/hr, KPH, Slayer task pace/ETA (with pause), respawns, loot & cannon usage",
        tags = {"combat", "xp", "slayer", "tracker", "hud", "loot", "cannon", "respawn"}
)
public class MonsterTrackerPlugin extends Plugin
{
    // ==================== Constants ====================
    private static final long DAMAGE_TIMEOUT_MS = 5_000L;
    private static final long KILL_DEDUP_TTL_MS = 10_000L;
    private static final long LOOT_DEBOUNCE_MS  = 1_500L;
    private static final long MIN_GPH_WINDOW_MS = 180_000L; // smooth early GP/hr

    private static final Pattern MSG_NEED_MORE =
            Pattern.compile("You need to kill\\s+(\\d+)\\s+more to complete your task\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_ASSIGNED =
            Pattern.compile("You're assigned to kill\\s+(\\d+)\\s+(.+?);\\s+only\\s+(\\d+)\\s+more to go\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_COMPLETE =
            Pattern.compile("You have completed your task!.*", Pattern.CASE_INSENSITIVE);

    // Cannon chat
    private static final Pattern MSG_CANNON_SETUP_A = Pattern.compile("You set up your cannon\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_SETUP_B = Pattern.compile("You assemble the cannon\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_SETUP_C = Pattern.compile("You place the cannon\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_PICKUP   = Pattern.compile("You pick up the cannon\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_EMPTY    = Pattern.compile("Your cannon is out of ammo\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_DECAYED  = Pattern.compile("Your cannon has decayed\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_LOAD1    = Pattern.compile("You load the cannon with\\s+(\\d+)\\s+cannonballs\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_CANNON_LOAD2    = Pattern.compile("You add\\s+(\\d+)\\s+cannonballs?\\s+to the cannon.*", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    // Overlay context menu text (strings must match Overlay)
    public static final String MENU_COPY      = "Copy stats to clipboard";
    public static final String MENU_PIN_XPH   = "Pin XP/hr";
    public static final String MENU_PIN_KPH   = "Pin Kills/hr";
    public static final String MENU_PIN_KL    = "Pin Kills Left";
    public static final String MENU_PIN_NL    = "Pin Next-Lvl Kills";
    public static final String MENU_UNPIN_ALL = "Unpin all";
    // (Optional) history actions – add these to Overlay menu if you want right-click control
    public static final String MENU_OPEN_HISTORY  = "Open Slayer History";
    public static final String MENU_CLEAR_HISTORY = "Clear Slayer History";

    // ==================== DI ====================
    @Inject private Client client;
    @Inject private MonsterTrackerConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private MonsterTrackerOverlay overlay;
    @Inject private ConfigManager configManager;
    @Inject private ItemManager itemManager;

    @Inject private Provider<MonsterRespawnOverlay> respawnOverlayProvider;
    private MonsterRespawnOverlay respawnOverlay;

    // ==================== UI windows ====================
    private JDialog historyDialog; private JTextArea historyArea;
    private JDialog faqDialog;     private JTextArea faqArea;

    // ==================== Leveling ====================
    private int kills = 0, killsWithXp = 0, totalXpGained = 0, lastSkillXp = 0;
    private int killsLeft = 0, killsToNextLevel = 0;
    private double xpPerHour = 0.0, timeLeftHours = 0.0;

    private long firstXpTime = 0L;
    private final Map<NPC, Long> recentlyDamaged = new HashMap<>();

    private long lastKillMillis = 0L;
    private final Map<Integer, Long> deathCountedIdx = new HashMap<>();
    private final Deque<Long> killTimes = new ArrayDeque<>();
    private final Deque<long[]> xpGains = new ArrayDeque<>();

    private final Deque<Integer> xpWindow = new ArrayDeque<>();
    private double dynamicCap = 0.0, dynamicFloor = 0.0;

    private String trackedNpcName = "";
    private String pendingNpcName = "";
    private int pendingTicks = 0, idleTicks = 0;

    // ==================== Slayer (with pause) ====================
    private String  slayerTaskName = "";
    private String  slayerTaskFamily = "";
    private Integer slayerAmount = null;
    private Integer lastSlayerAmount = null;
    private long slayerStartMillis = 0L;
    private long slayerCompletedAtMillis = 0L;
    private int  slayerDeltaKills = 0;
    private long lastSlayerUpdateMillis = 0L;

    // Active time accounting
    private boolean slayerPaused = false;
    private long slayerActiveAccum = 0L;      // ms of active (unpaused) time already counted
    private long slayerLastActiveStart = 0L;  // when current active segment started
    private long lastCombatActiveMillis = 0L; // when player last seen attacking

    // Per-task loot
    private long taskGpPickedTotal = 0L, taskGpSeenTotal = 0L;

    // ==================== Loot (session) ====================
    private long gpPickedTotal = 0L, gpSeenTotal = 0L, firstLootMillis = 0L;

    private static final class PendingLoot
    {
        long atMillis; String sourceName; boolean fromTask;
        Map<Integer, Integer> items = new HashMap<>();
    }
    private final Deque<PendingLoot> pendingLoot = new ArrayDeque<>();
    private Map<Integer, Integer> lastInvCounts = new HashMap<>();

    // ==================== Respawns ====================
    public static final class RespawnInfo
    {
        public long lastDeathMillis; public int bestSeconds;
        public RespawnInfo(long t, int s){ lastDeathMillis=t; bestSeconds=s; }
    }
    private final Map<WorldPoint, RespawnInfo> respawns = new HashMap<>();

    // ==================== Cannon ====================
    private boolean cannonActive = false;
    private long cannonStartMillis = 0L, cannonActiveMillis = 0L;
    private int  cannonBallsLoadedSession = 0;
    private long cannonGpSpentSession     = 0L;

    // ==================== Pins ====================
    private boolean pinXpHr=false, pinKph=false, pinKillsLeft=false, pinNextLvl=false;
    private int pinnedXpHr=0, pinnedKph=0, pinnedKillsLeft=0, pinnedNextLvl=0;

    // ==================== Personal history ====================
    private static final class FamilyHistory { double kph,xphr,gphr; int samples; }
    private final Map<String, FamilyHistory> famHistory = new HashMap<>();

    // ===========================================================
    // Lifecycle
    // ===========================================================
    @Provides MonsterTrackerConfig provideConfig(ConfigManager cm){ return cm.getConfig(MonsterTrackerConfig.class); }

    @Override protected void startUp()
    {
        overlayManager.add(overlay);

        if (config.respawnOverlay())
        {
            if (respawnOverlay == null) respawnOverlay = respawnOverlayProvider.get();
            overlayManager.add(respawnOverlay);
        }

        loadFamilyHistory();
        resetStats();
        if (config.slayerEnabled()) pullSlayerFromRuneLite();
    }

    @Override protected void shutDown()
    {
        saveCurrentSessionIntoHistory();
        storeFamilyHistory();

        overlayManager.remove(overlay);
        if (respawnOverlay != null) overlayManager.remove(respawnOverlay);

        resetStats();
        closeHistoryWindow();
        closeFaqWindow();
    }

    private Skill trackedSkill(){ return config.targetSkill().toSkill(); }

    private void resetStats()
    {
        kills=0; killsWithXp=0; totalXpGained=0;
        lastSkillXp = client!=null ? client.getSkillExperience(trackedSkill()) : 0;
        killsLeft=0; killsToNextLevel=0; xpPerHour=0.0; timeLeftHours=0.0; firstXpTime=0L;
        killTimes.clear(); xpGains.clear(); recentlyDamaged.clear();
        lastKillMillis=0L; deathCountedIdx.clear();
        xpWindow.clear(); dynamicCap=0.0; dynamicFloor=0.0;
        trackedNpcName=""; pendingNpcName=""; pendingTicks=0; idleTicks=0;

        // keep slayer/task state across target switches

        gpPickedTotal=0L; gpSeenTotal=0L; firstLootMillis=0L;
        pendingLoot.clear(); lastInvCounts.clear();

        respawns.clear();

        cannonActive=false; cannonStartMillis=0L; cannonActiveMillis=0L;
        cannonBallsLoadedSession=0; cannonGpSpentSession=0L;
    }

    private void softResetForNewTarget()
    {
        saveCurrentSessionIntoHistory();
        kills=0; killsWithXp=0; totalXpGained=0;
        killsLeft=0; killsToNextLevel=0; xpPerHour=0.0; timeLeftHours=0.0; firstXpTime=0L;
        killTimes.clear(); xpGains.clear(); xpWindow.clear();
        dynamicCap=0.0; dynamicFloor=0.0;
        recentlyDamaged.clear(); lastSkillXp = client.getSkillExperience(trackedSkill());
    }

    // ===========================================================
    // Slayer import + pause init
    // ===========================================================
    private void pullSlayerFromRuneLite()
    {
        final String rawName  = configManager.getRSProfileConfiguration("slayer", "taskName");
        final String amountStr= configManager.getRSProfileConfiguration("slayer", "amount");

        if (rawName != null && !rawName.trim().isEmpty())
        {
            String incomingName   = rawName.trim();
            String incomingFamily = canonicalizeTaskFamily(incomingName);

            // treat as new assignment when family changes
            if (slayerTaskFamily.isEmpty() || !incomingFamily.equalsIgnoreCase(slayerTaskFamily))
            {
                slayerTaskName   = incomingName;
                slayerTaskFamily = incomingFamily;
                slayerStartMillis = System.currentTimeMillis();
                slayerCompletedAtMillis = 0L;
                slayerDeltaKills = 0; lastSlayerAmount = null;
                taskGpPickedTotal = 0L; taskGpSeenTotal = 0L;

                slayerPaused = false;
                slayerActiveAccum = 0L;
                slayerLastActiveStart = slayerStartMillis;
                lastCombatActiveMillis = slayerStartMillis;
            }
            else
            {
                slayerTaskName = incomingName; // cosmetic rename update
            }
        }

        if (amountStr != null && !amountStr.trim().isEmpty())
        {
            try { applyExternalSlayerAmount(Integer.parseInt(amountStr.trim())); }
            catch (NumberFormatException ignored) {}
        }
    }

    private void applyExternalSlayerAmount(Integer amt)
    {
        if (amt == null) return;
        boolean changed = false;

        if (lastSlayerAmount == null)
        {
            lastSlayerAmount = amt; changed = true;
            if (slayerStartMillis == 0L) slayerStartMillis = System.currentTimeMillis();
        }
        else if (amt < lastSlayerAmount)
        {
            slayerDeltaKills += (lastSlayerAmount - amt);
            lastSlayerAmount = amt; changed = true;
        }
        else if (amt > lastSlayerAmount)
        {
            lastSlayerAmount = amt; changed = true;
        }

        if (amt == 0 && slayerCompletedAtMillis == 0L && !slayerTaskName.isEmpty())
        {
            slayerCompletedAtMillis = System.currentTimeMillis();
            appendTaskHistoryEntry();
            updateHistoryWindow();
        }

        slayerAmount = amt;
        if (changed) lastSlayerUpdateMillis = System.currentTimeMillis();
    }

    // ===========================================================
    // Config / FAQ / History
    // ===========================================================
    @Subscribe
    public void onConfigChanged(ConfigChanged e)
    {
        if (!"monstertracker".equals(e.getGroup())) return;

        if ("respawnOverlay".equals(e.getKey()))
        {
            if (config.respawnOverlay())
            {
                if (respawnOverlay == null) respawnOverlay = respawnOverlayProvider.get();
                overlayManager.add(respawnOverlay);
            }
            else if (respawnOverlay != null)
            {
                overlayManager.remove(respawnOverlay);
            }
            return;
        }

        if ("openHistoryTrigger".equals(e.getKey()) && config.openHistoryTrigger())
        {
            openHistoryWindow();
            configManager.setConfiguration("monstertracker", "openHistoryTrigger", false);
            return;
        }
        if ("clearHistoryTrigger".equals(e.getKey()) && config.clearHistoryTrigger())
        {
            clearHistory();
            configManager.setConfiguration("monstertracker", "clearHistoryTrigger", false);
            return;
        }
        if ("openFaq".equals(e.getKey()) && config.openFaq())
        {
            openFaqWindow();
            configManager.setConfiguration("monstertracker", "openFaq", false);
            return;
        }

        // Recompute goal stats if target changes
        if ("targetLevel".equals(e.getKey()) || "targetSkill".equals(e.getKey()))
        {
            lastSkillXp = client.getSkillExperience(trackedSkill());
            if (killsWithXp > 0) updateStats();
            else { killsLeft = 0; killsToNextLevel = 0; timeLeftHours = 0.0; xpPerHour = 0.0; }
        }
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked e)
    {
        if (e.getOverlay() != overlay) return;
        final String opt = e.getEntry().getOption();

        if (MENU_COPY.equals(opt))
        {
            String text = buildClipboardText();
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Combat Progress HUD: stats copied.", "");
            } catch (Exception ignored) {}
            return;
        }

        switch (opt)
        {
            case MENU_PIN_XPH: pinXpHr = true;  pinnedXpHr  = (int)Math.round(getXpPerHour());     toast("Pinned XP/hr: " + pinnedXpHr); break;
            case MENU_PIN_KPH: pinKph  = true;  pinnedKph   = (int)Math.round(getKillsPerHour());  toast("Pinned Kills/hr: " + pinnedKph); break;
            case MENU_PIN_KL:  pinKillsLeft = true; pinnedKillsLeft = getKillsLeft();              toast("Pinned Kills Left: " + pinnedKillsLeft); break;
            case MENU_PIN_NL:  pinNextLvl = true;  pinnedNextLvl = getKillsToNextLevel();         toast("Pinned Next-Lvl Kills: " + pinnedNextLvl); break;
            case MENU_UNPIN_ALL: pinXpHr = pinKph = pinKillsLeft = pinNextLvl = false;            toast("Cleared all pins."); break;

            case MENU_OPEN_HISTORY:
                openHistoryWindow();
                toast("Opened Slayer task history.");
                break;
            case MENU_CLEAR_HISTORY:
                clearHistory();
                toast("Cleared Slayer task history.");
                break;
        }
    }
    private void toast(String s){ try{ client.addChatMessage(ChatMessageType.GAMEMESSAGE,"","Combat Progress HUD: "+s,""); }catch(Exception ignored){} }

    // ===========================================================
    // Target detection + pause logic
    // ===========================================================
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (config.slayerEnabled()) pullSlayerFromRuneLite();

        Player me = client.getLocalPlayer();
        if (me == null) return;

        final long now = System.currentTimeMillis();
        boolean attackingNow = false;

        Actor interacting = me.getInteracting();
        if (interacting instanceof NPC)
        {
            attackingNow = true;
            String name = ((NPC) interacting).getName();
            if (name != null && !name.isEmpty())
            {
                idleTicks = 0;

                if (name.equalsIgnoreCase(trackedNpcName))
                {
                    pendingNpcName = ""; pendingTicks = 0;
                }
                else
                {
                    if (name.equalsIgnoreCase(pendingNpcName)) pendingTicks++;
                    else { pendingNpcName = name; pendingTicks = 1; }

                    if (pendingTicks >= Math.max(1, config.detectLatchTicks()))
                    {
                        if (!trackedNpcName.isEmpty() && !name.equalsIgnoreCase(trackedNpcName))
                            softResetForNewTarget();
                        trackedNpcName = pendingNpcName; pendingNpcName = ""; pendingTicks = 0;
                    }
                }
            }
        }
        else
        {
            idleTicks++;
            if (idleTicks >= Math.max(2, config.detectLoseTicks()))
            {
                pendingNpcName = ""; pendingTicks = 0; trackedNpcName = "";
            }
        }

        // Slayer pause/resume accounting
        if (config.slayerEnabled() && slayerAmount != null && slayerAmount > 0 && slayerStartMillis > 0)
        {
            long thresholdMs = Math.max(1, config.slayerPauseIdleMinutes()) * 60_000L;

            if (attackingNow)
            {
                lastCombatActiveMillis = now;
                if (slayerPaused)
                {
                    slayerPaused = false;
                    slayerLastActiveStart = now; // resume
                }
            }
            else
            {
                if (!slayerPaused && (now - lastCombatActiveMillis) >= thresholdMs)
                {
                    slayerActiveAccum += Math.max(0L, now - slayerLastActiveStart);
                    slayerPaused = true;
                }
            }
        }

        if (killsWithXp > 0) updateStats();
    }

    // ===========================================================
    // Chat (Slayer + Cannon)
    // ===========================================================
    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        ChatMessageType t = e.getType();
        if (t != ChatMessageType.GAMEMESSAGE && t != ChatMessageType.SPAM) return;

        final String msg = e.getMessage();

        // Slayer
        if (config.slayerEnabled())
        {
            Matcher mAssigned = MSG_ASSIGNED.matcher(msg);
            if (mAssigned.matches())
            {
                String name = mAssigned.group(2).trim();
                String fam  = canonicalizeTaskFamily(name);
                int remaining = Integer.parseInt(mAssigned.group(3));

                if (slayerTaskFamily.isEmpty() || !fam.equalsIgnoreCase(slayerTaskFamily))
                {
                    slayerTaskName = name; slayerTaskFamily = fam;
                    slayerStartMillis = System.currentTimeMillis();
                    slayerCompletedAtMillis = 0L;
                    slayerDeltaKills = 0; lastSlayerAmount = null;
                    taskGpPickedTotal = 0L; taskGpSeenTotal = 0L;

                    slayerPaused = false; slayerActiveAccum = 0L;
                    slayerLastActiveStart = slayerStartMillis;
                    lastCombatActiveMillis = slayerStartMillis;
                }
                else slayerTaskName = name;

                applyExternalSlayerAmount(remaining);
                return;
            }

            Matcher mNeed = MSG_NEED_MORE.matcher(msg);
            if (mNeed.matches()) { int remaining = Integer.parseInt(mNeed.group(1)); applyExternalSlayerAmount(remaining); return; }

            if (MSG_COMPLETE.matcher(msg).matches()) { applyExternalSlayerAmount(0); }
        }

        // Cannon
        if (!config.cannonEnabled()) return;

        if (MSG_CANNON_SETUP_A.matcher(msg).matches()
                || MSG_CANNON_SETUP_B.matcher(msg).matches()
                || MSG_CANNON_SETUP_C.matcher(msg).matches())
        { setCannonActive(true); return; }

        if (MSG_CANNON_PICKUP.matcher(msg).matches() || MSG_CANNON_DECAYED.matcher(msg).matches())
        { setCannonActive(false); return; }

        if (MSG_CANNON_EMPTY.matcher(msg).matches()) { return; }

        Matcher load1 = MSG_CANNON_LOAD1.matcher(msg);
        Matcher load2 = MSG_CANNON_LOAD2.matcher(msg);
        if (load1.matches() || load2.matches())
        {
            int added = Integer.parseInt(load1.matches() ? load1.group(1) : load2.group(1));
            recordCannonLoad(added);
            setCannonActive(true);
        }
    }

    private void setCannonActive(boolean active)
    {
        if (!config.cannonEnabled()) return;
        long now = System.currentTimeMillis();
        if (active && !cannonActive){ cannonActive = true; cannonStartMillis = now; }
        else if (!active && cannonActive){ cannonActive = false; if (cannonStartMillis > 0L){ cannonActiveMillis += (now - cannonStartMillis); cannonStartMillis = 0L; } }
    }
    private void recordCannonLoad(int count){ if (count>0){ cannonBallsLoadedSession += count; cannonGpSpentSession += wikiPriceOf(ItemID.CANNONBALL, count); } }

    // ===========================================================
    // Damage / Kill / XP
    // ===========================================================
    public String getActiveMonsterName(){ return trackedNpcName==null ? "" : trackedNpcName; }

    @Subscribe public void onHitsplatApplied(HitsplatApplied event)
    {
        if (event.getActor() instanceof NPC && event.getHitsplat().isMine())
        {
            NPC npc=(NPC)event.getActor();
            String want=getActiveMonsterName(); if (want.isEmpty()) return;
            if (npc.getName()!=null && npc.getName().equalsIgnoreCase(want))
                recentlyDamaged.put(npc, System.currentTimeMillis());
        }
    }

    @Subscribe public void onActorDeath(ActorDeath event)
    {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc=(NPC)event.getActor(); String name=npc.getName(); String want=getActiveMonsterName();
        if (name==null || want.isEmpty() || !name.equalsIgnoreCase(want)) return;

        Long lastHitTime = recentlyDamaged.get(npc); recentlyDamaged.remove(npc);
        if (lastHitTime==null || System.currentTimeMillis()-lastHitTime > DAMAGE_TIMEOUT_MS) return;

        countKillAndXp(); noteDeathCounted(npc.getIndex()); registerPotentialSlayerKill(name);
    }

    private void countKillAndXp()
    {
        kills++; killTimes.addLast(System.currentTimeMillis());

        int currentXp = client.getSkillExperience(trackedSkill());
        int gained = currentXp - lastSkillXp; lastSkillXp = currentXp;

        if (gained > 0)
        {
            xpGains.addLast(new long[]{System.currentTimeMillis(), gained});
            pushSample(gained);

            int manualCap   = Math.max(10, config.maxXpPerKill());
            int manualFloor = Math.max(1,  config.minXpPerKillFloor());

            double learnedCap   = (dynamicCap   > 0.0) ? dynamicCap   : manualCap;
            double learnedFloor = (dynamicFloor > 0.0) ? Math.max(dynamicFloor, manualFloor) : manualFloor;

            double capToUse   = config.autoCapXpPerKill() ? learnedCap   : manualCap;
            double floorToUse = config.autoCapXpPerKill() ? learnedFloor : manualFloor;

            if (gained >= floorToUse && gained <= Math.min(capToUse, config.hardAbsoluteCap()))
            {
                if (killsWithXp == 0) firstXpTime = System.currentTimeMillis();
                killsWithXp++; totalXpGained += gained; updateStats();
            }
        }

        lastKillMillis = System.currentTimeMillis();
        trimWindows();
    }

    private void noteDeathCounted(int idx){ if (idx<0) return; long now=System.currentTimeMillis(); deathCountedIdx.put(idx, now); deathCountedIdx.entrySet().removeIf(e -> now-e.getValue()>KILL_DEDUP_TTL_MS); }
    private boolean deathAlreadyCounted(int idx){ Long t=deathCountedIdx.get(idx); if(t==null) return false; long now=System.currentTimeMillis(); if(now-t>KILL_DEDUP_TTL_MS){ deathCountedIdx.remove(idx); return false; } return true; }

    private void pushSample(int gained)
    {
        xpWindow.addLast(gained);
        int max = Math.max(config.autoCapWindow(), 10);
        while (xpWindow.size() > max) xpWindow.removeFirst();
        computeDynamicBounds();
    }
    private void computeDynamicBounds()
    {
        if (xpWindow.isEmpty()){ dynamicCap=0.0; dynamicFloor=0.0; return; }
        int[] arr = xpWindow.stream().mapToInt(Integer::intValue).sorted().toArray();
        double med = median(arr);
        double[] dev = Arrays.stream(arr).mapToDouble(v -> Math.abs(v-med)).sorted().toArray();
        double mad = median(dev);
        double cap = med + 6.0*mad, floor = med - 3.0*mad;
        if (Double.isNaN(cap)) cap=0.0; if (Double.isNaN(floor)) floor=0.0;
        dynamicCap = Math.max(10.0, Math.min(cap, config.hardAbsoluteCap()));
        dynamicFloor = Math.max(1.0, Math.min(floor, dynamicCap));
    }
    private static double median(int[] s){ int n=s.length; if(n==0)return 0.0; return (n&1)==1? s[n/2] : (s[n/2-1]+s[n/2])/2.0; }
    private static double median(double[] s){ int n=s.length; if(n==0)return 0.0; return (n&1)==1? s[n/2] : (s[n/2-1]+s[n/2])/2.0; }

    private void trimWindows()
    {
        long cutoff = System.currentTimeMillis() - 5*60_000L;
        while (!killTimes.isEmpty() && killTimes.peekFirst() < cutoff) killTimes.removeFirst();
        while (!xpGains.isEmpty() && xpGains.peekFirst()[0] < cutoff) xpGains.removeFirst();
    }

    private void updateStats()
    {
        if (killsWithXp <= 0)
        {
            killsLeft=0; killsToNextLevel=0; xpPerHour=0.0; timeLeftHours=0.0; return;
        }

        double avgXpPerKill = (double) totalXpGained / (double) killsWithXp;

        int currentXp  = client.getSkillExperience(trackedSkill());
        int currentLvl = Experience.getLevelForXp(currentXp);
        int targetCfg  = Math.min(Math.max(config.targetLevel(), 1), 99);
        int nextLevel  = Math.min(99, currentLvl + 1);

        int targetXp    = Experience.getXpForLevel(Math.max(targetCfg, nextLevel));
        int nextLevelXp = Experience.getXpForLevel(nextLevel);

        int xpToTarget = targetXp - currentXp;
        int xpToNext   = nextLevelXp - currentXp;

        killsToNextLevel = avgXpPerKill > 0.0 ? (int)Math.ceil(Math.max(0, xpToNext)/avgXpPerKill) : 0;

        if (xpToTarget <= 0)
        {
            killsLeft=0;
            long elapsedMillis = Math.max(System.currentTimeMillis()-firstXpTime, 1L);
            double hoursElapsed = elapsedMillis / 3_600_000.0;
            xpPerHour = totalXpGained / hoursElapsed;
            timeLeftHours = 0.0;
            return;
        }

        killsLeft = avgXpPerKill > 0.0 ? (int)Math.ceil(xpToTarget/avgXpPerKill) : 0;

        long elapsedMillis = Math.max(System.currentTimeMillis()-firstXpTime, 1L);
        double hoursElapsed = elapsedMillis / 3_600_000.0;

        xpPerHour = totalXpGained / hoursElapsed;
        double kph = killsWithXp / hoursElapsed;
        timeLeftHours = kph > 0.0 ? killsLeft / kph : 0.0;
    }

    // ===========================================================
    // Loot / pricing
    // ===========================================================
    private int wikiPriceOf(int itemId, int qty)
    {
        if (qty <= 0) return 0;
        if (itemId == ItemID.COINS_995) return qty;
        int price = 0;
        try { price = itemManager.getItemPrice(itemId); } catch (Exception ignored) {}
        if (price <= 0) { try { price = itemManager.getItemComposition(itemId).getHaPrice(); } catch (Exception ignored) {} }
        return Math.max(0, price) * qty;
    }
    private Map<Integer,Integer> toCounts(ItemContainer c){ Map<Integer,Integer> m=new HashMap<>(); if(c==null)return m; for(Item it:c.getItems()){ if(it==null) continue; int id=it.getId(), q=it.getQuantity(); if(id>0 && q>0) m.merge(id,q,Integer::sum);} return m; }
    private Map<Integer,Integer> positiveDelta(Map<Integer,Integer>b,Map<Integer,Integer>a){ Map<Integer,Integer>d=new HashMap<>(); for(Map.Entry<Integer,Integer> e:a.entrySet()){ int id=e.getKey(), newQ=e.getValue(), oldQ=b.getOrDefault(id,0), inc=newQ-oldQ; if(inc>0) d.put(id,inc);} return d; }

    private void sweepPendingLoot()
    {
        long cutoff=System.currentTimeMillis()-120_000L;
        while(!pendingLoot.isEmpty())
        {
            PendingLoot h=pendingLoot.peekFirst(); if(h==null) break;
            boolean empty = h.items.values().stream().allMatch(q-> q==null || q<=0);
            if (empty || h.atMillis<cutoff) pendingLoot.removeFirst(); else break;
        }
    }

    private long matchPickedToPending(Map<Integer,Integer> picked)
    {
        long gp=0L;
        for (Map.Entry<Integer,Integer> e:picked.entrySet())
        {
            int id=e.getKey(), need=e.getValue(); if (need<=0) continue;
            Iterator<PendingLoot> it=pendingLoot.iterator();
            while (it.hasNext() && need>0)
            {
                PendingLoot b=it.next(); int have=b.items.getOrDefault(id,0); if (have<=0) continue;
                int take=Math.min(have,need); b.items.put(id, have - take); need -= take;
                int val = wikiPriceOf(id, take); gp += val; if (b.fromTask) taskGpPickedTotal += val;
                boolean empty = b.items.values().stream().allMatch(q-> q==null || q<=0); if (empty) it.remove();
            }
        }
        return gp;
    }

    private boolean isFromCurrentTask(String npcName)
    {
        if (!config.slayerEnabled()) return false;
        if (slayerAmount == null || slayerAmount <= 0) return false;
        if (slayerTaskFamily.isEmpty()) return false;
        String srcFam  = canonicalizeNpcFamily(npcName);
        String taskFam = slayerTaskFamily;
        return srcFam.equalsIgnoreCase(taskFam)
                || srcFam.toLowerCase().contains(taskFam.toLowerCase())
                || taskFam.toLowerCase().contains(srcFam.toLowerCase());
    }

    @Subscribe public void onNpcLootReceived(NpcLootReceived e)
    {
        final NPC npc=e.getNpc(); final String src = npc!=null ? npc.getName() : null; if (src==null || src.isEmpty()) return;

        String currentFam = canonicalizeNpcFamily(getActiveMonsterName());
        String displayFam = canonicalizeNpcFamily(getDisplayMonsterName());
        String srcFam     = canonicalizeNpcFamily(src);
        if (currentFam.isEmpty() && displayFam.isEmpty()){ trackedNpcName=src; currentFam=srcFam; }
        else if (currentFam.isEmpty()) currentFam = displayFam;
        if (!srcFam.equalsIgnoreCase(currentFam)) return;

        PendingLoot batch=new PendingLoot(); batch.atMillis=System.currentTimeMillis(); batch.sourceName=src; batch.fromTask=isFromCurrentTask(src);
        int value=0; for (ItemStack is:e.getItems()){ int id=is.getId(); int q=Math.max(1,is.getQuantity()); batch.items.merge(id,q,Integer::sum); value+=wikiPriceOf(id,q); }
        pendingLoot.addLast(batch); gpSeenTotal+=value; if(batch.fromTask) taskGpSeenTotal+=value;
        while(pendingLoot.size()>64) pendingLoot.removeFirst(); sweepPendingLoot();

        if (npc!=null && deathAlreadyCounted(npc.getIndex())) return;
        long now=System.currentTimeMillis(); if (now-lastKillMillis >= LOOT_DEBOUNCE_MS){ countKillAndXp(); registerPotentialSlayerKill(src); }
    }

    @Subscribe public void onServerNpcLoot(ServerNpcLoot e)
    {
        PendingLoot batch=new PendingLoot(); batch.atMillis=System.currentTimeMillis(); batch.sourceName="NPC"; batch.fromTask=(slayerAmount!=null && slayerAmount>0);
        int value=0; for (ItemStack is:e.getItems()){ int id=is.getId(); int q=Math.max(1,is.getQuantity()); batch.items.merge(id,q,Integer::sum); value+=wikiPriceOf(id,q); }
        pendingLoot.addLast(batch); gpSeenTotal+=value; if(batch.fromTask) taskGpSeenTotal+=value;
        while(pendingLoot.size()>64) pendingLoot.removeFirst(); sweepPendingLoot();
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged e)
    {
        final ItemContainer c=e.getItemContainer(); if(c==null) return;
        if (c.getId()!=InventoryID.INVENTORY.getId()) return;
        Map<Integer,Integer> now=toCounts(c);
        Map<Integer,Integer> inc=positiveDelta(lastInvCounts, now);
        lastInvCounts=now;

        if (!inc.isEmpty())
        {
            long added = matchPickedToPending(inc);
            if (added>0){ gpPickedTotal += added; if (firstLootMillis==0L) firstLootMillis=System.currentTimeMillis(); }
            sweepPendingLoot();
        }
    }

    // ===========================================================
    // Respawns
    // ===========================================================
    @Subscribe public void onNpcDespawned(NpcDespawned e)
    {
        NPC n=e.getNpc(); if(n==null || n.getName()==null) return;
        if (!n.getName().equalsIgnoreCase(getActiveMonsterName())) return;
        Long hit=recentlyDamaged.get(n); if (hit==null || System.currentTimeMillis()-hit>DAMAGE_TIMEOUT_MS) return;
        WorldPoint wp=n.getWorldLocation();
        RespawnInfo ri=respawns.get(wp);
        if (ri==null) ri = new RespawnInfo(System.currentTimeMillis(),0);
        else ri.lastDeathMillis=System.currentTimeMillis();
        respawns.put(wp,ri);
    }
    @Subscribe public void onNpcSpawned(NpcSpawned e)
    {
        NPC n=e.getNpc(); if(n==null) return;
        WorldPoint wp=n.getWorldLocation(); RespawnInfo ri=respawns.get(wp);
        if (ri!=null && ri.lastDeathMillis>0)
        {
            int secs=(int)Math.max(0,(System.currentTimeMillis()-ri.lastDeathMillis)/1000L);
            if (ri.bestSeconds==0 || secs<ri.bestSeconds) ri.bestSeconds=secs;
        }
    }

    // ===========================================================
    // Slayer helpers (with pause)
    // ===========================================================
    public boolean isOnTaskNow()
    {
        if (!config.slayerEnabled()) return false;
        if (slayerAmount == null || slayerAmount <= 0) return false;
        if (slayerTaskFamily.isEmpty()) return false;

        String current = canonicalizeNpcFamily(getActiveMonsterName()).toLowerCase();
        if (current.isEmpty()) return false;

        String taskFam = slayerTaskFamily.toLowerCase();
        return current.contains(taskFam) || taskFam.contains(current);
    }

    public boolean shouldShowSlayerPanel()
    {
        if (!config.slayerEnabled() || !config.showSlayerBlock()) return false;
        // vanish when finished
        return slayerAmount != null && slayerAmount > 0 && slayerTaskName != null && !slayerTaskName.isEmpty();
    }

    private boolean isOnTaskFamily(String npcName)
    {
        if (!config.slayerEnabled()) return false;
        if (npcName==null || npcName.isEmpty()) return false;
        if (slayerTaskFamily==null || slayerTaskFamily.isEmpty()) return false;
        String srcFam  = canonicalizeNpcFamily(npcName).toLowerCase();
        String taskFam = slayerTaskFamily.toLowerCase();
        return srcFam.contains(taskFam) || taskFam.contains(srcFam);
    }

    private void registerPotentialSlayerKill(String npcName)
    {
        if (!config.slayerEnabled()) return;
        if (slayerAmount == null || slayerAmount <= 0) return;
        if (!isOnTaskFamily(npcName)) return;

        long now = System.currentTimeMillis();
        if (now - lastSlayerUpdateMillis < 2000L) return; // avoid double-decrement

        slayerAmount = Math.max(0, slayerAmount - 1);
        slayerDeltaKills++;
        lastSlayerAmount = slayerAmount;
        lastSlayerUpdateMillis = now;

        if (slayerAmount == 0 && slayerCompletedAtMillis == 0L && !slayerTaskName.isEmpty())
        {
            slayerCompletedAtMillis = now;
            appendTaskHistoryEntry();
            updateHistoryWindow();
        }
    }

    // active time (ms) with pauses excluded
    private long slayerActiveMillis()
    {
        if (slayerStartMillis == 0L) return 0L;
        long acc = slayerActiveAccum;
        if (!slayerPaused && slayerAmount != null && slayerAmount > 0)
            acc += Math.max(0L, System.currentTimeMillis() - slayerLastActiveStart);
        return Math.max(0L, acc);
    }

    public boolean isSlayerPaused(){ return slayerPaused; }

    public double getSlayerTaskKph()
    {
        if (!config.slayerEnabled()) return 0.0;
        long active = slayerActiveMillis();
        if (active <= 0L || slayerDeltaKills <= 0) return 0.0;
        return slayerDeltaKills / (active / 3_600_000.0);
    }

    public double getSlayerTimeLeftHours()
    {
        if (!config.slayerEnabled()) return 0.0;
        if (slayerAmount == null || slayerAmount <= 0) return 0.0;
        double kph = getSlayerTaskKph();
        return kph > 0.0 ? slayerAmount / kph : 0.0;
    }

    public int getSlayerElapsedSeconds(){ long ms = slayerActiveMillis(); return (int)Math.max(0L, ms/1000L); }
    public String getSlayerTaskName() { return slayerTaskName == null ? "" : slayerTaskName; }
    public Integer getSlayerRemaining() { return slayerAmount; }

    // ===========================================================
    // Overlay API
    // ===========================================================
    public String getDisplayMonsterName()
    {
        if (config.slayerEnabled() && isOnTaskNow())
        {
            String task = getSlayerTaskName();
            if (!task.isEmpty()) return task;
        }
        return canonicalizeNpcFamily(getActiveMonsterName());
    }

    public String getTrackedSkillName(){ return trackedSkill().getName(); }
    public int getKills(){ return kills; }
    public boolean hasAnyData(){ return killsWithXp > 0; }
    public int getKillsLeft(){ return killsLeft; }
    public int getKillsToNextLevel(){ return killsToNextLevel; }
    public double getXpPerHour(){ return xpPerHour; }
    public double getTimeLeftHours(){ return timeLeftHours; }
    public double getKillsPerHour(){ if(killsWithXp<=0) return 0.0; double h=Math.max((System.currentTimeMillis()-firstXpTime)/3_600_000.0,1e-9); return killsWithXp/h; }

    private void ensureTrim(){ trimWindows(); }
    private double getShortWindowXpPerHour(){ ensureTrim(); long sum=0; for(long[] e:xpGains) sum+=e[1]; return (sum<=0)?0.0:(sum*12.0); }
    private double getShortWindowKph(){ ensureTrim(); return killTimes.size()*12.0; }

    public String getXpPaceArrow(){ double s=getShortWindowXpPerHour(), b=xpPerHour; if(s<=0||b<=0) return ""; if(s>b*1.03) return "▲"; if(s<b*0.97) return "▼"; return "▶"; }
    public String getKphPaceArrow(){ double s=getShortWindowKph(), b=getKillsPerHour(); if(s<=0||b<=0) return ""; if(s>b*1.03) return "▲"; if(s<b*0.97) return "▼"; return "▶"; }

    public int getPinnedOrLiveXpPerHour(){ return pinXpHr? pinnedXpHr : (int)Math.round(getXpPerHour()); }
    public int getPinnedOrLiveKillsPerHour(){ return pinKph? pinnedKph : (int)Math.round(getKillsPerHour()); }
    public int getPinnedOrLiveKillsLeft(){ return pinKillsLeft? pinnedKillsLeft : getKillsLeft(); }
    public int getPinnedOrLiveKillsToNextLevel(){ return pinNextLvl? pinnedNextLvl : getKillsToNextLevel(); }

    public Color getConfidenceColor()
    {
        int n=xpWindow.size(); if(n < Math.max(5, config.autoCapMinKills())) return new Color(255,120,120);
        int[] arr = xpWindow.stream().mapToInt(Integer::intValue).sorted().toArray();
        double med = median(arr);
        double[] dev = Arrays.stream(arr).mapToDouble(v->Math.abs(v-med)).sorted().toArray();
        double mad = median(dev);
        double rel = (med<=1.0)?1.0:(mad/med);
        if (rel<0.15) return new Color(120,255,120);
        if (rel<0.35) return new Color(255,230,120);
        return new Color(255,160,120);
    }

    // Cannon getters
    public boolean isCannonEnabled(){ return config.cannonEnabled() && config.showCannonBlock(); }
    public boolean isCannonActive(){ return cannonActive; }
    public int  getCannonBallsLoaded(){ return cannonBallsLoadedSession; }
    public long getCannonGpSpent(){ return cannonGpSpentSession; }
    public int  getCannonUptimeSeconds(){ if(!config.cannonEnabled()) return 0; long active=cannonActiveMillis + (cannonActive && cannonStartMillis>0 ? (System.currentTimeMillis()-cannonStartMillis):0L); return (int)Math.max(0L,active/1000L); }
    public int  getCannonGpPerHour(){ if(!config.cannonEnabled()) return 0; long active=cannonActiveMillis + (cannonActive && cannonStartMillis>0 ? (System.currentTimeMillis()-cannonStartMillis):0L); double h=Math.max(active/3_600_000.0,1e-6); return (int)Math.round(cannonGpSpentSession/h); }

    // Skip ROI object
    public static final class SkipRoi { public String label, etaStr; public int kph; public Integer gphr; public Color color; }
    public SkipRoi getSkipRoi()
    {
        if (!config.slayerShowSkipRoi()) return null;
        if (!isOnTaskNow() || slayerAmount == null || slayerAmount <= 0) return null;

        String fam = canonicalizeNpcFamily(getDisplayMonsterName());
        double kphNow = Math.max(0.0, getKillsPerHour());
        double kphEst = kphNow > 0 ? kphNow : getHistoryKph(fam); if (kphEst<=0) return null;

        double hoursToFinish = slayerAmount / kphEst;
        int gphrNow = gpPerHourPicked(); if (gphrNow==0) gphrNow=(int)Math.round(getHistoryGphr(fam));

        double medKph = personalMedian(true), medGph = personalMedian(false);
        int sensPct = Math.max(10, Math.min(90, config.slayerRoiSensitivityPct())); double thresh = sensPct/100.0;

        boolean slow = (medKph > 0 && kphEst < medKph * thresh);
        boolean poor = (medGph > 0 && gphrNow < medGph * thresh);

        SkipRoi r=new SkipRoi();
        if (slow || poor){ r.label="Consider Skip"; r.color=new Color(255,160,120); }
        else              { r.label="Keep";         r.color=new Color(120,255,120); }
        r.etaStr="~"+formatTime(hoursToFinish); r.kph=(int)Math.round(kphEst);
        r.gphr=(gphrNow>0? gphrNow : null);
        return r;
    }

    private void saveCurrentSessionIntoHistory()
    {
        if (killsWithXp<=0) return;
        String fam = canonicalizeNpcFamily(getDisplayMonsterName());
        if (fam.isEmpty()) fam = canonicalizeNpcFamily(getActiveMonsterName());
        if (fam.isEmpty()) return;

        double kph=getKillsPerHour(), xph=getXpPerHour(), gph=gpPerHourPicked();
        if (kph<=0 && xph<=0 && gph<=0) return;

        FamilyHistory fh = famHistory.getOrDefault(fam, new FamilyHistory());
        int n = Math.max(1, fh.samples);
        fh.kph  = (fh.kph*n + kph)  / (n+1);
        fh.xphr = (fh.xphr*n + xph) / (n+1);
        fh.gphr = (fh.gphr*n + gph) / (n+1);
        fh.samples = n+1;
        famHistory.put(fam, fh);
        storeFamilyHistory();
    }
    private double getHistoryKph(String fam){ FamilyHistory fh=famHistory.get(fam); return fh==null?0.0:fh.kph; }
    private double getHistoryGphr(String fam){ FamilyHistory fh=famHistory.get(fam); return fh==null?0.0:fh.gphr; }
    private double personalMedian(boolean kph){ if(famHistory.isEmpty()) return 0.0; double[] arr=famHistory.values().stream().mapToDouble(f-> kph?f.kph:f.gphr).filter(v->v>0).sorted().toArray(); if(arr.length==0) return 0.0; return (arr.length%2==1)?arr[arr.length/2]:(arr[arr.length/2-1]+arr[arr.length/2])/2.0; }
    private void loadFamilyHistory(){ famHistory.clear(); String blob=configManager.getConfiguration("monstertracker","famHistoryBlob"); if(blob==null||blob.isEmpty()) return; try{ for(String row:blob.split(";")){ if(row.trim().isEmpty()) continue; String[] p=row.split("\\|"); FamilyHistory fh=new FamilyHistory(); String fam=p[0]; fh.kph=Double.parseDouble(p[1]); fh.xphr=Double.parseDouble(p[2]); fh.gphr=Double.parseDouble(p[3]); fh.samples=Integer.parseInt(p[4]); famHistory.put(fam,fh);} }catch(Exception ignored){} }
    private void storeFamilyHistory(){ try{ String blob=famHistory.entrySet().stream().map(e-> e.getKey()+"|"+e.getValue().kph+"|"+e.getValue().xphr+"|"+e.getValue().gphr+"|"+e.getValue().samples).collect(Collectors.joining(";")); configManager.setConfiguration("monstertracker","famHistoryBlob",blob);}catch(Exception ignored){} }

    // ===========================================================
    // Clipboard summary
    // ===========================================================
    private String buildClipboardText()
    {
        String name=getDisplayMonsterName();
        StringBuilder sb=new StringBuilder();
        sb.append("Combat Progress HUD — ").append(name.isEmpty()?"No target":name).append('\n');
        sb.append("Skill: ").append(getTrackedSkillName()).append('\n');
        sb.append("Kills: ").append(getKills()).append('\n');
        sb.append("XP/hr: ").append(String.format("%,d",(int)Math.round(getXpPerHour()))).append('\n');
        sb.append("Kills/hr: ").append(String.format("%,d",(int)Math.round(getKillsPerHour()))).append('\n');
        sb.append("Kills Left (goal): ").append(getKillsLeft()).append('\n');
        sb.append("Next Level Kills: ").append(getKillsToNextLevel()).append('\n');

        int gphr=gpPerHourPicked();
        if (config.showGpHrRow() && gphr>0)
        {
            sb.append("GP picked/hr: ").append(String.format("%,d",gphr)).append('\n');
            sb.append("GP picked total: ").append(String.format("%,d",getGpPickedTotal())).append('\n');
        }

        if (isCannonEnabled())
        {
            sb.append("Cannon: ").append(isCannonActive()?"Active":"Idle").append('\n');
            sb.append("Balls used: ").append(getCannonBallsLoaded()).append('\n');
            int cGphr=getCannonGpPerHour(); if(cGphr>0) sb.append("Cannon GP/hr: ").append(String.format("%,d",cGphr)).append('\n');
            sb.append("Cannon Up: ").append(formatSeconds(getCannonUptimeSeconds())).append('\n');
        }

        if (shouldShowSlayerPanel())
        {
            sb.append("Task: ").append(getSlayerTaskName()).append('\n');
            Integer left=getSlayerRemaining(); if(left!=null) sb.append("Task Left: ").append(left).append('\n');
            sb.append("Task Pace: ").append(String.format("%,d kph",(int)Math.round(getSlayerTaskKph()))).append('\n');
            sb.append("Task ETA: ").append(getSlayerTimeLeftHours()>0? formatTime(getSlayerTimeLeftHours()) : "-").append('\n');
            sb.append("Task Time: ").append(formatSeconds(getSlayerElapsedSeconds())).append(isSlayerPaused()? " (Paused)" : "").append('\n');
            sb.append("Task GP picked: ").append(String.format("%,d", taskGpPickedTotal));
            if (taskGpSeenTotal>0) sb.append(" (seen: ").append(String.format("%,d",taskGpSeenTotal)).append(')');
            sb.append('\n');
        }
        return sb.toString();
    }

    // ===========================================================
    // Loot rate (session)
    // ===========================================================
    public boolean hasLootRate(){ return firstLootMillis>0 && gpPickedTotal>0; }
    public int gpPerHourPicked()
    {
        if (firstLootMillis==0L || gpPickedTotal<=0L) return 0;
        long now=System.currentTimeMillis();
        long elapsed=Math.max(1L, now-firstLootMillis);
        long effective=Math.max(elapsed, MIN_GPH_WINDOW_MS);
        double h=effective/3_600_000.0;
        return (int)Math.round(gpPickedTotal/h);
    }
    public long getGpPickedTotal(){ return gpPickedTotal; }

    // ===========================================================
    // Respawn overlay accessors
    // ===========================================================
    public Map<WorldPoint, RespawnInfo> respawnInfo(){ return respawns; }

    /** Backward-compatible helper for overlays; no reflection. */
    public boolean isRespawnOverlayEnabled()
    {
        return config.respawnOverlay();
    }

    // ===========================================================
    // Name helpers
    // ===========================================================
    private static String singularize(String s){ return s.endsWith("s") ? s.substring(0,s.length()-1) : s; }
    private static String normalizeSpaces(String s){ return s.replace('\u00A0',' ').replaceAll("\\s+"," ").trim(); }
    private static String capitalize(String s){ return s.isEmpty()? s : Character.toUpperCase(s.charAt(0))+s.substring(1); }

    private String canonicalizeNpcFamily(String name)
    {
        if (name==null) return "";
        String n=normalizeSpaces(name); if(n.isEmpty()) return n;
        String[] parts=n.split(" "); String first=parts[0]; String last=parts[parts.length-1];
        if (last.equalsIgnoreCase("dragon")) return "Dragon";
        if (last.equalsIgnoreCase("demon") || last.equalsIgnoreCase("demons")) return "Demon";
        return capitalize(first);
    }
    private String canonicalizeTaskFamily(String task)
    {
        if (task==null) return "";
        String n=normalizeSpaces(task).toLowerCase(); if(n.isEmpty()) return n;
        String[] parts=n.split(" "); String last=singularize(parts[parts.length-1]);
        if (last.equals("dragon") || last.equals("demon")) return capitalize(last);
        return capitalize(parts[0]);
    }

    // ===========================================================
    // Task history window
    // ===========================================================
    private void appendTaskHistoryEntry()
    {
        int secs = getSlayerElapsedSeconds();
        String line = TS.format(Instant.ofEpochMilli(slayerCompletedAtMillis))
                + " | " + getSlayerTaskName()
                + " | " + formatSeconds(secs)
                + " | GP picked: " + String.format("%,d", taskGpPickedTotal)
                + (taskGpSeenTotal>0? " (seen: "+String.format("%,d",taskGpSeenTotal)+")":"");

        String prev = configManager.getConfiguration("monstertracker", "taskHistory");
        String updated = (prev == null || prev.isEmpty()) ? line : (prev + "\n" + line);

        String[] lines = updated.split("\\R");
        if (lines.length > 50)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 50; i < lines.length; i++)
            {
                if (sb.length() > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            updated = sb.toString();
        }

        configManager.setConfiguration("monstertracker", "taskHistory", updated);
    }

    private void openHistoryWindow()
    {
        SwingUtilities.invokeLater(() -> {
            if (historyDialog == null)
            {
                historyArea = new JTextArea(20, 48);
                historyArea.setEditable(false);
                historyArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
                JScrollPane scroll = new JScrollPane(historyArea);

                historyDialog = new JDialog((Frame) null, "Slayer Task History");
                historyDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                historyDialog.add(scroll);
                historyDialog.pack();
                historyDialog.setLocationRelativeTo(null);
            }
            updateHistoryWindow();
            historyDialog.setVisible(true);
        });
    }

    private void updateHistoryWindow()
    {
        if (historyArea == null) return;
        String text = configManager.getConfiguration("monstertracker", "taskHistory");
        historyArea.setText(text == null ? "" : text);
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }

    private void clearHistory()
    {
        configManager.unsetConfiguration("monstertracker", "taskHistory");
        updateHistoryWindow();
    }

    private void closeHistoryWindow()
    {
        if (historyDialog != null)
        {
            historyDialog.setVisible(false);
            historyDialog.dispose();
            historyDialog = null;
            historyArea = null;
        }
    }

    // ===========================================================
    // FAQ window
    // ===========================================================
    private void openFaqWindow()
    {
        SwingUtilities.invokeLater(() -> {
            if (faqDialog == null)
            {
                faqArea = new JTextArea(24, 60);
                faqArea.setEditable(false);
                faqArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
                faqArea.setText(buildFaq());
                JScrollPane scroll = new JScrollPane(faqArea);

                faqDialog = new JDialog((Frame) null, "Combat Progress HUD — FAQ");
                faqDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                faqDialog.add(scroll);
                faqDialog.pack();
                faqDialog.setLocationRelativeTo(null);
            }
            else
            {
                faqArea.setText(buildFaq());
            }
            faqDialog.setVisible(true);
        });
    }

    private void closeFaqWindow()
    {
        if (faqDialog != null)
        {
            faqDialog.setVisible(false);
            faqDialog.dispose();
            faqDialog = null;
            faqArea = null;
        }
    }

    private String buildFaq()
    {
        return ""
                + "FAQ — Combat Progress HUD\n"
                + "\n"
                + "• XP/hr & KPH arrows: ▲ improving, ▼ slowing, ▶ steady (compares last ~5 min vs session).\n"
                + "• Kills Left: estimated kills to your target level using valid kill XP only.\n"
                + "• Next Level Kills: kills to your next level (ignores target setting).\n"
                + "• GP/hr: counts only items you pick up; stabilized for the first 3 minutes.\n"
                + "• Slayer Task:\n"
                + "    - Task Time/Pace/ETA use ACTIVE time. If idle > 'Pause after idle' minutes,\n"
                + "      the timer pauses and resumes automatically when you attack again.\n"
                + "    - The task panel hides when remaining reaches 0.\n"
                + "• Skip ROI: compares your current KPH/GP/hr against your personal medians across NPCs and\n"
                + "  suggests Keep or Consider Skip, with an ETA at your observed KPH.\n"
                + "• Pins: Right-click the overlay → pin XP/hr, KPH, Kills Left, or Next-level Kills.\n"
                + "• Copy: Right-click → Copy stats to clipboard for easy sharing.\n"
                + "• Cannon: shows uptime, balls used, and cannon GP/hr (when enabled).\n"
                + "\n"
                + "Tips: Use the Display section of settings to toggle any row/section on/off.\n";
    }

    // ===========================================================
    // Utility
    // ===========================================================
    private static String formatSeconds(int t){ int h=t/3600,m=(t%3600)/60,s=t%60; return String.format("%02d:%02d:%02d",h,m,s); }
    private String formatTime(double hours){ return formatSeconds((int)Math.max(Math.round(hours*3600.0),0)); }
}
