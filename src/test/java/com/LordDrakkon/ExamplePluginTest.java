package com.LordDrakkon;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(MonsterTrackerPlugin.class);
        RuneLite.main(args);
    }
}
