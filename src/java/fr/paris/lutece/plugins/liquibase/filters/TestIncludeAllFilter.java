package fr.paris.lutece.plugins.liquibase.filters;

import java.sql.SQLException;

import fr.paris.lutece.plugins.liquibase.LiquibaseRunnerContext;
import fr.paris.lutece.plugins.liquibase.PluginMeta;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.utils.sql.PluginVersion;
import fr.paris.lutece.utils.sql.SqlPathInfo;
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
        if (!changeLogPath.endsWith(".sql"))
        {
            include = false;
        } else if (info == null)
        {
            AppLogService.info("LiquibaseRunner could not determine what to do with file {}", changeLogPath);
            include = false;
        } else
        {
            AppLogService.debug("LiquibaseRunner testing file with info " + info);
            final String pluginName = info.getFullPluginName();
            // empty DB : only "create/init" files
            if (LiquibaseRunnerContext.isEmptyDb())
            {
                include = info.isCreate();
                if(include)
                {
                    LiquibaseRunnerContext.setPluginLastRunScriptType(pluginName, LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_CREATE);
                 }
            }
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
                        if(include)
                        {
                           LiquibaseRunnerContext.setPluginLastRunScriptType(pluginName, LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_CREATE);
                        }

                    } else if (!info.isCreate())
                    {
                        // DB exists, liquibase already ran, a version exists => run the (chosen) updates
                        include = info.getDstVersion().compareTo(alreadyInstalledVersion) > 0;

                        if(!include &&  (alreadyInstalledVersion.isSnapshot()&& LiquibaseRunnerContext.isAcceptSnapshotVersion() )||(alreadyInstalledVersion.isUnstable()&& LiquibaseRunnerContext.isAcceptUnstableVersion())  && LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_UPDATE.equals(LiquibaseRunnerContext.pluginLastRunScriptType(pluginName)))
                        {    // if we accept snapshot and unstable versions (rc,beta,..) include also if the dst version is an unstable or  snapshot and equals to the installed version
                            include = info.getDstVersion().compareTo(alreadyInstalledVersion) == 0;
                        }
                        //finaly if we included an update script, we set the last run script type to update
                        if(include)
                        {
                         LiquibaseRunnerContext.setPluginLastRunScriptType(pluginName, LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_UPDATE); 
                        }
  

                    }
                    
                  

                } catch (SQLException e)
                {
                    AppLogService.error("version retrieve failed for plugin " + pluginName, e);
                }
            }
            // in all cases, store the current version in the datastore
            String pluginVersion= PluginMeta.getPluginVersion(pluginName);
            if (pluginVersion == null)
                AppLogService.error("LiquibaseRunner. No plugin metadata for " + pluginName);
            else
                LiquibaseRunnerContext.setPluginVersion(pluginName, pluginVersion);
        }
        AppLogService.info("LiquibaseRunner : file {} {}included", changeLogPath, include ? "" : "NOT ");
        return include;
    }
}
