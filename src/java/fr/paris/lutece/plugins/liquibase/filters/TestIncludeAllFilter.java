package fr.paris.lutece.plugins.liquibase.filters;

import java.sql.SQLException;

import fr.paris.lutece.plugins.liquibase.LiquibaseRunnerContext;
import fr.paris.lutece.plugins.liquibase.PluginMeta;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
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
        if (LiquibaseRunnerContext.isEnableMigrationMode() || !changeLogPath.endsWith(".sql"))
        {
            include = false;
        } 
        else if (info == null)
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
                    setLastRunScriptType(info);
                 }
            }
            else
            {
                try
                {
                    PluginVersion alreadyInstalledVersion = LiquibaseRunnerContext.componentVersion(info);
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
                              setLastRunScriptType(info);
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
                             setLastRunScriptType(info);
                        }
  
                    }
                    
                  

                } catch (SQLException e)
                {
                    AppLogService.error("version retrieve failed for plugin " + pluginName, e);
                }
            }
            // in all cases, store the current version in the datastore
            if(!info.isTheme())
            {
                //cas plugin,module,core
                String pluginVersion=PluginMeta.getPluginVersion(pluginName);
                if (pluginVersion == null)
                    AppLogService.error("LiquibaseRunner. No plugin metadata for " + pluginName);
                else
                    LiquibaseRunnerContext.setComponentVersion(pluginName, pluginVersion,false);
            }
            else
            {
                String themeVersion=AppPropertiesService.getProperty("themes."+info.getTheme()+".version");
                if (themeVersion == null)
                    AppLogService.error("LiquibaseRunner. No theme metadata for " + info.getTheme());
                else
                    LiquibaseRunnerContext.setComponentVersion(info.getTheme(), themeVersion,true);
            }
        }
        AppLogService.info("LiquibaseRunner : file {} {}included", changeLogPath, include ? "" : "NOT ");
        return include;
    }



    /**
     * Sets the last run script type for the given SQL path info.
     * @param info the SQL path info
     */
    private void setLastRunScriptType(SqlPathInfo info)
    {
        if(info.isTheme())
        {
            LiquibaseRunnerContext.setThemeLastRunScriptType(info.getTheme(), info.isCreate() ? LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_CREATE : LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_UPDATE);
        }
        else
        {
            LiquibaseRunnerContext.setPluginLastRunScriptType(info.getFullPluginName(), info.isCreate() ? LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_CREATE : LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_UPDATE);
        }
        
    }
}
