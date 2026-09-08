package fr.paris.lutece.plugins.liquibase.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fr.paris.lutece.plugins.liquibase.PluginMeta;

/**
 * Validates the reserved pre-execution script convention (LUT-33327) : path selection for the plugin and
 * module forms, WEB-INF/classes/ normalization, and rejection of misplaced or misnamed prerun_db_* files.
 * The fixtures under src/test/resources/sql/plugins/ppp ship one valid file of each form.
 */
public class PrerunScriptsTest
{
    private static final String PLUGIN_FORM = "sql/plugins/ppp/plugin/prerun_db_ppp.sql";
    private static final String MODULE_FORM = "sql/plugins/ppp/modules/mm/plugin/prerun_db_ppp-mm.sql";

    @BeforeAll
    public static void declarePlugins()
    {
        PluginMeta.getPluginsMeta().put("ppp", "1.0.0");
        PluginMeta.getPluginsMeta().put("ppp-mm", "1.0.0");
    }

    @Test
    public void validFormsResolveTheirComponent()
    {
        assertEquals("ppp", PrerunScripts.component(PLUGIN_FORM));
        assertEquals("ppp-mm", PrerunScripts.component(MODULE_FORM));
        assertEquals("ppp", PrerunScripts.component("WEB-INF/classes/" + PLUGIN_FORM), "paths are normalized");
        assertTrue(PrerunScripts.isPrerunName(PLUGIN_FORM));
        assertTrue(PrerunScripts.any(), "the fixtures ship prerun files");
    }

    @Test
    public void invalidFormsAreRejected()
    {
        // file name not matching the component derived from the directories
        assertNull(PrerunScripts.component("sql/plugins/ppp/plugin/prerun_db_other.sql"));
        // wrong subdirectory
        assertNull(PrerunScripts.component("sql/plugins/ppp/upgrade/prerun_db_ppp.sql"));
        // module file named after the module alone instead of <plugin>-<module>
        assertNull(PrerunScripts.component("sql/plugins/ppp/modules/mm/plugin/prerun_db_mm.sql"));
        // not a prerun name at all
        assertFalse(PrerunScripts.isPrerunName("sql/plugins/ppp/plugin/create_db_ppp.sql"));
    }

    @Test
    public void filtersSplitTheScriptsBetweenTheTwoRuns()
    {
        PrerunIncludeAllFilter prerunFilter = new PrerunIncludeAllFilter();
        assertTrue(prerunFilter.include(PLUGIN_FORM));
        assertTrue(prerunFilter.include(MODULE_FORM));
        assertFalse(prerunFilter.include("sql/plugins/ppp/plugin/create_db_ppp.sql"), "ordinary scripts stay in the main run");
    }
}
