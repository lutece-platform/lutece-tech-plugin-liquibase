package fr.paris.lutece.plugins.liquibase.filters;

import junit.framework.TestCase;

public class PluginVersionTest extends TestCase
{
    private static final String V_1_0_0 = "1.0.0";
    private static final String V_1_1_1 = "1.1.1";
    private static final String V_1_0_9 = "1.0.9";
    private static final String V_1_0_10 = "1.0.10";

    public void test()
    {
        assertTrue(PluginVersion.of(V_1_0_0).compareTo(PluginVersion.of(V_1_0_0)) == 0);
        assertTrue(PluginVersion.of(V_1_0_0).compareTo(PluginVersion.of(V_1_1_1)) < 0);
        assertTrue(PluginVersion.of(V_1_1_1).compareTo(PluginVersion.of(V_1_0_10)) > 0);
        assertTrue(PluginVersion.of(V_1_0_9).compareTo(PluginVersion.of(V_1_0_10)) < 0);
        assertTrue(PluginVersion.of(V_1_0_0).compareTo(PluginVersion.of(V_1_0_9)) < 0);
    }
}
