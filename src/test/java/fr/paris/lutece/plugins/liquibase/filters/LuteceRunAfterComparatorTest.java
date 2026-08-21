package fr.paris.lutece.plugins.liquibase.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fr.paris.lutece.plugins.liquibase.PluginMeta;

/**
 * Validates the topological ordering produced by {@link LuteceRunAfterComparator} from runAfter directives
 * declared in the headers of the SQL fixtures under src/test/resources/sql/plugins :
 *
 * <ul>
 * <li>aaa declares runAfter:bbb (in its create script only), bbb declares runAfter:ccc, ccc declares nothing
 * : the expected order is ccc, bbb, aaa — the exact opposite of the alphabetical order liquibase would use</li>
 * <li>mmm declares nothing and keeps its natural position</li>
 * <li>qqq declares runAfter:doesnotexist : invalid target, natural position kept</li>
 * <li>xxx and yyy declare runAfter on each other : cycle, broken deterministically without failing</li>
 * </ul>
 */
public class LuteceRunAfterComparatorTest
{
    private static final String AAA_CREATE = "sql/plugins/aaa/plugin/create_db_aaa.sql";
    private static final String AAA_UPDATE = "sql/plugins/aaa/upgrade/update_db_aaa-1.0.0-1.1.0.sql";
    private static final String BBB_CREATE = "sql/plugins/bbb/plugin/create_db_bbb.sql";
    private static final String CCC_CREATE = "sql/plugins/ccc/plugin/create_db_ccc.sql";
    private static final String CCC_UPDATE = "sql/plugins/ccc/upgrade/update_db_ccc-1.0.0-1.1.0.sql";
    private static final String MMM_CREATE = "sql/plugins/mmm/plugin/create_db_mmm.sql";
    private static final String QQQ_CREATE = "sql/plugins/qqq/plugin/create_db_qqq.sql";
    private static final String XXX_CREATE = "sql/plugins/xxx/plugin/create_db_xxx.sql";
    private static final String YYY_CREATE = "sql/plugins/yyy/plugin/create_db_yyy.sql";

    @BeforeAll
    public static void declarePlugins()
    {
        // runAfter targets must resolve to declared plugins : populate the map preloadMeta() would fill
        for (String plugin : Arrays.asList("aaa", "bbb", "ccc", "mmm", "qqq", "xxx", "yyy"))
        {
            PluginMeta.getPluginsMeta().put(plugin, "1.0.0");
        }
    }

    private List<String> sortedFixtures()
    {
        // deliberately fed out of order
        List<String> paths = new ArrayList<>(Arrays.asList(AAA_UPDATE, MMM_CREATE, CCC_UPDATE, BBB_CREATE, YYY_CREATE, AAA_CREATE, QQQ_CREATE, XXX_CREATE,
                CCC_CREATE));
        paths.sort(new LuteceRunAfterComparator());
        return paths;
    }

    private static void assertBefore(List<String> sorted, String first, String second)
    {
        int firstIndex = sorted.indexOf(first);
        int secondIndex = sorted.indexOf(second);
        assertTrue(firstIndex >= 0, first + " missing from " + sorted);
        assertTrue(secondIndex >= 0, second + " missing from " + sorted);
        assertTrue(firstIndex < secondIndex, first + " should sort before " + second + " but order was " + sorted);
    }

    @Test
    public void chainedRunAfterReversesAlphabeticalOrder()
    {
        List<String> sorted = sortedFixtures();
        // chain aaa -> bbb -> ccc : execution order must be ccc, then bbb, then aaa
        assertBefore(sorted, CCC_CREATE, BBB_CREATE);
        assertBefore(sorted, CCC_UPDATE, BBB_CREATE);
        assertBefore(sorted, BBB_CREATE, AAA_CREATE);
        // ccc's own scripts keep their internal alphabetical order
        assertBefore(sorted, CCC_CREATE, CCC_UPDATE);
    }

    @Test
    public void directiveIsPluginScoped()
    {
        List<String> sorted = sortedFixtures();
        // aaa's update script declares no directive itself, but the whole plugin is relocated
        assertBefore(sorted, BBB_CREATE, AAA_UPDATE);
        // and aaa's scripts keep their internal alphabetical order
        assertBefore(sorted, AAA_CREATE, AAA_UPDATE);
    }

    @Test
    public void unrelatedPluginsKeepTheirNaturalPositions()
    {
        List<String> sorted = sortedFixtures();
        // mmm and qqq (invalid target, directive ignored) stay in plain alphabetical order,
        // after the whole ccc chain ('~' sorts after any alphanumeric) and before xxx/yyy
        assertBefore(sorted, AAA_UPDATE, MMM_CREATE);
        assertBefore(sorted, MMM_CREATE, QQQ_CREATE);
        assertBefore(sorted, QQQ_CREATE, XXX_CREATE);
    }

    @Test
    public void cycleIsBrokenDeterministicallyWithoutFailing()
    {
        List<String> sorted = sortedFixtures();
        // xxx <-> yyy : plugins are resolved in name order, so the cycle is broken at xxx
        // (its directive is discarded, it keeps its natural position) and yyy lands after it
        assertBefore(sorted, XXX_CREATE, YYY_CREATE);
        assertEquals(9, sorted.size());
    }

    @Test
    public void bothFormsOfTheSamePathCompareEqualForTreeSetDeduplication()
    {
        assertEquals(0, new LuteceRunAfterComparator().compare("WEB-INF/classes/" + CCC_CREATE, CCC_CREATE));
    }
}
