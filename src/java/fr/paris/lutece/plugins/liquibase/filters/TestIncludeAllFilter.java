package fr.paris.lutece.plugins.liquibase.filters;

import java.sql.SQLException;

import fr.paris.lutece.plugins.liquibase.LiquibaseRunnerContext;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.util.AppLogService;
import liquibase.changelog.IncludeAllFilter;

/**
 * Filter for use in the liquibase changelog config.
 * 
 * Authorizes execution of SQL files depending of the auto-detected use case (first init, update ...)
 */
public class TestIncludeAllFilter implements IncludeAllFilter
{
    @Override
    public boolean include(String changeLogPath)
    {
        // no explicit check can be done here on the "file" represented by changeLogPath, since it might not be a file, but a classpath entry
        boolean include = false;
        SqlPathInfo info = SqlPathInfo.parse(changeLogPath);
        if (info == null)
        {
            AppLogService.info("LiquibaseRunner could not determine what to do with file {}", changeLogPath);
            include = false;
        } else
        {
            AppLogService.debug("LiquibaseRunner testing file with info " + info);
            final String pluginName = info.getPlugin();
            // empty DB : only "create/init" files
            if (LiquibaseRunnerContext.isEmptyDb())
                include = info.isCreate();
            else
            {
                try
                {
                    PluginVersion alreadyInstalledVersion = LiquibaseRunnerContext.pluginVersion(pluginName);
                    if (LiquibaseRunnerContext.isLiquibaseNeverRan())
                    {
                        // DB exists, never ran liquibase => consider it's a migration
                        include = false;
                    } else if (alreadyInstalledVersion == null)
                    {
                        // DB exists, liquibase already ran, but no version => that's a new plugin we're installing
                        include = info.isCreate();
                    } else if (!info.isCreate())
                    {
                        // DB exists, liquibase already ran, a version exists => run the (chosen) updates
                        include = info.getDstVersion().compareTo(alreadyInstalledVersion) > 0;
                    }
                } catch (SQLException e)
                {
                    AppLogService.error("version retrieve failed for plugin " + pluginName, e);
                }
            }
            // in all cases, store the current version in the datastore
            LiquibaseRunnerContext.setPluginVersion(pluginName, PluginService.getPluginMeta(pluginName).getVersion());
        }
        AppLogService.info("LiquibaseRunner : file {} {}included", changeLogPath, include ? "" : "NOT ");
        return include;
    }

    // FIXME : create a JUnit
    public static void main(String[] args)
    {
        final String[] files = new String[] { "sql/plugins/testpourliquibase/plugin/create_db_testpourliquibase.sql",
                "sql/plugins/testpourliquibase/upgrade/update_db_testpourliquibase-0.0.9-1.0.0.sql", "sql/upgrade/update_db_lutece_core-2.3.0-2.3.1.sql",
                "sql/init_db_lutece_core.sql", "sql/plugins/regularexpression/upgrade/update_db_regularexpression_3.0.0_3.0.1.sql",
                "sql/upgrade/update_db_lutece_core-7.0.9-7.0.10.sql",
                "sql/plugins/bignumberplugin/upgrade/update_db_whatever-654.123.789-78999.6546546.321321321.sql" };
        for (String file : files)
        {
            System.out.println("file : " + file);
            SqlPathInfo info = SqlPathInfo.parse(file);
            System.out.println("    info : " + info);
        }
    }
}
