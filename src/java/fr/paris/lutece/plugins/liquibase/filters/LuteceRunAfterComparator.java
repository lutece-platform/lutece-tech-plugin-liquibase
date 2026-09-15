package fr.paris.lutece.plugins.liquibase.filters;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import fr.paris.lutece.plugins.liquibase.PluginMeta;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.utils.sql.RunAfterOrdering;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.Resource;

/**
 * Comparator for use in the liquibase changelog config (resourceComparator attribute of includeAll).
 *
 * Orders SQL files as liquibase does by default (alphabetical path order), except for plugins declaring
 * an explicit ordering directive in the leading comment block of any of their scripts :
 *
 * <pre>
 * --liquibase formatted sql
 * --lutece runAfter:genericattributes
 * --changeset author:id
 * </pre>
 *
 * The rules (plugin-scoped relocation after the target, chained directives, invalid directives ignored
 * with an ERROR log : conflicts, unknown or script-less target, self reference, core, cycles) live in
 * {@link RunAfterOrdering} of library-sql-utils, shared with the Ant database initialization of
 * build-config so that both mechanisms produce the same order. This class is the liquibase adapter :
 * it scans the SQL resources of the classpath, checks the targets against the declared plugins
 * ({@link PluginMeta}) and routes the ordering decisions to {@link AppLogService}.
 *
 * Like the rest of this plugin, this class is deliberately not thread-safe : liquibase runs in the single
 * startup thread. Instantiated by liquibase itself (no-arg constructor), outside any DI.
 */
public class LuteceRunAfterComparator implements Comparator<String>
{
    private static final String WEB_INF_CLASSES = "WEB-INF/classes/";
    private static final String LOG_PREFIX = "LiquibaseRunner. ";

    private static final RunAfterOrdering.Listener LISTENER = new RunAfterOrdering.Listener()
    {
        @Override
        public void info(String message)
        {
            AppLogService.info(LOG_PREFIX + message);
        }

        @Override
        public void error(String message)
        {
            AppLogService.error(LOG_PREFIX + message);
        }
    };

    /** built on first use */
    private RunAfterOrdering ordering;

    @Override
    public int compare(String left, String right)
    {
        // Liquibase stores resources in a TreeSet based on this comparator : returning 0 deduplicates
        // the two forms of the same path (with and without WEB-INF/classes/), as the standard comparator
        // does. Distinct normalized paths always yield distinct keys since the key embeds the whole path.
        RunAfterOrdering current = ordering();
        return current.keyOf(normalize(left)).compareTo(current.keyOf(normalize(right)));
    }

    static String normalize(String path)
    {
        return path.replace(WEB_INF_CLASSES, "");
    }

    private RunAfterOrdering ordering()
    {
        if (ordering == null)
        {
            ordering = buildOrdering();
        }
        return ordering;
    }

    private static RunAfterOrdering buildOrdering()
    {
        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor())
        {
            List<RunAfterOrdering.Script> scripts = new ArrayList<>();
            for (Resource resource : accessor.search("sql", true))
            {
                scripts.add(new LiquibaseScript(resource));
            }
            return RunAfterOrdering.build(scripts, target -> PluginMeta.getPluginVersion(target) != null, LISTENER);
        } catch (Exception e)
        {
            AppLogService.error(LOG_PREFIX + "Could not scan SQL files for runAfter directives : falling back to alphabetical order", e);
            return RunAfterOrdering.build(Collections.emptyList(), target -> true, RunAfterOrdering.SILENT);
        }
    }

    /** A liquibase resource seen as a script : path without the WEB-INF/classes/ prefix. */
    private static final class LiquibaseScript implements RunAfterOrdering.Script
    {
        private final Resource resource;

        LiquibaseScript(Resource resource)
        {
            this.resource = resource;
        }

        @Override
        public String getPath()
        {
            return normalize(resource.getPath());
        }

        @Override
        public InputStream open() throws IOException
        {
            return resource.openInputStream();
        }
    }
}
