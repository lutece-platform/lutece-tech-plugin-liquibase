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
            final String componentName = info.isTheme() ? info.getTheme() : pluginName;
            // LUT-33232 : resolve the declared version BEFORE deciding anything.
            // A path resolving to a component that no descriptor (or themes.<name>.version property) declares
            // is a packaging fault, NOT a new plugin : a genuinely new plugin always ships its descriptor.
            // Treating it as new used to run create_db_* scripts (and their DROP TABLE) on populated databases.
            final String declaredVersion = info.isTheme() ? AppPropertiesService.getProperty("themes." + info.getTheme() + ".version")
                    : PluginMeta.getPluginVersion(pluginName);
            if (declaredVersion == null)
            {
                LiquibaseRunnerContext.reportUnresolvedComponent(changeLogPath, componentName);
                include = false;
            }
            // empty DB : only "create/init" files
            else if (LiquibaseRunnerContext.isEmptyDb())
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

                        if(!include &&  LiquibaseRunnerContext.LAST_RUN_SCRIPT_TYPE_UPDATE.equals(LiquibaseRunnerContext.pluginLastRunScriptType(pluginName)) && ((alreadyInstalledVersion.isSnapshot()&& LiquibaseRunnerContext.isAcceptSnapshotVersion() )||(alreadyInstalledVersion.isUnstable()&& LiquibaseRunnerContext.isAcceptUnstableVersion())))
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
            // in all cases (except unresolved components, excluded above), store the current version in the datastore
            if (declaredVersion != null)
            {
                LiquibaseRunnerContext.setComponentVersion(componentName, declaredVersion, info.isTheme());
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
