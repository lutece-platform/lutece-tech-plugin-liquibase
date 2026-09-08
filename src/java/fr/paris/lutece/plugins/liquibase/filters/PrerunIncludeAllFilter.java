package fr.paris.lutece.plugins.liquibase.filters;

import fr.paris.lutece.plugins.liquibase.LiquibaseRunnerContext;
import fr.paris.lutece.plugins.liquibase.PluginMeta;
import liquibase.changelog.IncludeAllFilter;

/**
 * Filter of the preliminary changelog (db/changelog-pre.xml) : selects ONLY the reserved pre-execution
 * scripts (see {@link PrerunScripts}), executed before the main changelog is built and filtered
 * (LUT-33326). The main changelog filter ({@link TestIncludeAllFilter}) excludes them symmetrically.
 *
 * No version logic applies here : a pre-execution changeset is candidate at every startup, liquibase alone
 * decides through DATABASECHANGELOG (run once) and the changeset's preconditions.
 *
 * A prerun_db_* file at an unexpected location, misnamed, or owned by an undeclared component is a
 * packaging fault, reported like an unresolved component (startup aborted under liquibase.safeRun=true).
 *
 * Instantiated by liquibase itself (no-arg constructor), outside any DI.
 */
public class PrerunIncludeAllFilter implements IncludeAllFilter
{
    @Override
    public boolean include(String changeLogPath)
    {
        if (!changeLogPath.endsWith(".sql") || !PrerunScripts.isPrerunName(changeLogPath))
        {
            return false;
        }
        String component = PrerunScripts.component(changeLogPath);
        if (component == null || PluginMeta.getPluginVersion(component) == null)
        {
            LiquibaseRunnerContext.reportInvalidPrerun(changeLogPath);
            return false;
        }
        return true;
    }
}
