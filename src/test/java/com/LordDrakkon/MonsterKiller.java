package com.LordDrakkon;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class MonsterKiller
{
    // Silence: "Unchecked generics array creation for varargs parameter"
    @SuppressWarnings({ "unchecked", "varargs" })
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(MonsterTrackerPlugin.class);
        RuneLite.main(args);
    }
}
