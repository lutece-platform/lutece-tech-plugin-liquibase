package fr.paris.lutece.plugins.liquibase.filters;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.paris.lutece.portal.service.util.AppLogService;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.Resource;

/**
 * The reserved pre-execution script of a component (LUT-33326) : one liquibase formatted SQL file per
 * component, at a fixed path derived from the component name,
 *
 * <pre>
 * sql/plugins/&lt;plugin&gt;/plugin/prerun_db_&lt;plugin&gt;.sql
 * sql/plugins/&lt;plugin&gt;/modules/&lt;module&gt;/plugin/prerun_db_&lt;plugin&gt;-&lt;module&gt;.sql
 * </pre>
 *
 * All such files are executed in a preliminary liquibase update (db/changelog-pre.xml), BEFORE the main
 * changelog is built and filtered, and are excluded from the main run. They hold the one-shot fix-ups that
 * must be visible to the version resolution of the main run : typically, after a plugin rename, migrating
 * the datastore keys (instance-prefixed forms included) and rewriting the DATABASECHANGELOG FILENAME rows to
 * the new component directory. A formatted SQL file may hold several changesets : each release needing a
 * fix-up appends one to the same file, which is the component's pre-execution mini-changelog.
 *
 * Authoring rules : such changesets run on every database state, including an empty one at first install,
 * so each must be guarded by preconditions failing to MARK_RAN both onFail and onError (a referenced table
 * may not exist yet) ; a fix-up already executed under another path must declare its original identity with
 * logicalFilePath on its changeset line ; and, like any executed changeset, the body must not change
 * afterwards without a validCheckSum declaration. Execution order between prerun files is alphabetical by
 * path (runAfter does not apply to the preliminary run).
 *
 * Selection is a pure path test : no file content is read. A file named prerun_db_* that does not sit at
 * its component's expected path is a packaging fault, reported like an unresolved component (startup
 * aborted when liquibase.safeRun=true).
 *
 * Like the rest of this plugin, this class is deliberately not thread-safe : liquibase runs in the single
 * startup thread.
 */
public final class PrerunScripts
{
    private static final String WEB_INF_CLASSES = "WEB-INF/classes/";
    private static final String PRERUN_FILE_PREFIX = "prerun_db_";
    /** the expected shape : component directory, plugin/ subdirectory, file named after the component */
    private static final Pattern PRERUN_PATTERN = Pattern
            .compile("^sql/plugins/(?<plugin>[\\p{Alnum}\\-]+)(?:/modules/(?<module>[\\p{Alnum}]+))?/plugin/prerun_db_(?<component>[\\p{Alnum}\\-]+)\\.sql$");

    /** whether at least one prerun_db_* file (valid or not) is present. Built on first use. */
    private static Boolean anyPresent;

    private PrerunScripts( )
    {
    }

    /** Whether the file name (whatever its location) claims to be a pre-execution script. */
    public static boolean isPrerunName(String path)
    {
        String normalized = normalize(path);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return fileName.startsWith(PRERUN_FILE_PREFIX) && fileName.endsWith(".sql");
    }

    /**
     * The component owning a well-formed pre-execution script path, or null when the path is not at the
     * expected location or the file name does not match the component derived from the directories.
     */
    public static String component(String path)
    {
        Matcher matcher = PRERUN_PATTERN.matcher(normalize(path));
        if (!matcher.matches())
        {
            return null;
        }
        String module = matcher.group("module");
        String component = module == null ? matcher.group("plugin") : matcher.group("plugin") + "-" + module;
        return component.equals(matcher.group("component")) ? component : null;
    }

    /** Whether at least one prerun_db_* file ships in the webapp : the preliminary update runs only then. */
    public static boolean any()
    {
        if (anyPresent == null)
        {
            anyPresent = Boolean.FALSE;
            try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor())
            {
                Set<String> seen = new HashSet<>();
                for (Resource resource : accessor.search("sql", true))
                {
                    String path = normalize(resource.getPath());
                    if (seen.add(path) && isPrerunName(path))
                    {
                        anyPresent = Boolean.TRUE;
                        break;
                    }
                }
            } catch (Exception e)
            {
                AppLogService.error("LiquibaseRunner. Could not scan SQL paths for prerun_db files : preliminary update skipped", e);
            }
        }
        return anyPresent.booleanValue();
    }

    static String normalize(String path)
    {
        return path.replace(WEB_INF_CLASSES, "");
    }
}
