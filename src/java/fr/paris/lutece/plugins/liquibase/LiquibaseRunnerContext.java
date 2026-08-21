package fr.paris.lutece.plugins.liquibase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import fr.paris.lutece.portal.service.datastore.DatastoreService;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.utils.sql.PluginVersion;
import fr.paris.lutece.utils.sql.SqlPathInfo;

/**
 * Static context holder for shared data during a liquibase run.
 * 
 * This class offers a static API so that filters instantiated by liquibase can access its state.
 */
public class LiquibaseRunnerContext
{
     /** Last run script type (create/init) */
    public static final String LAST_RUN_SCRIPT_TYPE_CREATE="create/init";
    /** Last run script type (update) */
    public static final String LAST_RUN_SCRIPT_TYPE_UPDATE="update";
    
    private static final String CORE_PLUGIN_NAME = "core";
    /**
     * This (configurable) request determines whether liquibase has ever been run or not : it tests the existence of liquibase specific tables.
     * 
     * A result of 0 means no liquibase ever run
     */
    private static final String SQL__FIRST_LIQUIBASE_RUN_EVER = "liquibase.first.run.request";
    /**
     * This (configurable) request determines whether the database is empty or not : it counts the number of tables in the current DB.
     * 
     * A result of 0 means first run
     */
    private static final String SQL__EMPTY_DB = "liquibase.empty.db.request";

   
    
    private static final String LIQUIBASE_ACCEPT_SNAPSHOT_VERSIONS = "liquibase.accept.snapshot.versions";
    private static final String LIQUIBASE_ACCEPT_UNSTABLE_VERSIONS = "liquibase.accept.unstable.versions";
    private static final String LIQUIBASE_SAFE_RUN = "liquibase.safeRun";


    private static boolean liquibaseNeverRan, emptyDb, bAcceptSnapshotVersion, bAcceptUnstableVersion, bEnabledDryRun,bEnableMigrationMode,bSafeRun;
    public static boolean isEmptyDb()
    {
        return emptyDb;
    }

    public static boolean isLiquibaseNeverRan()
    {
        return liquibaseNeverRan;
    }
    public static boolean isAcceptSnapshotVersion()
    {
        return bAcceptSnapshotVersion;
    }
    public static boolean isAcceptUnstableVersion()
    {
        return bAcceptUnstableVersion;
    }

    /** Migration mode is enabled when the property "liquibase.migration.mode" is set to true */
    public static boolean isEnableMigrationMode()
    {
        return bEnableMigrationMode;
    }

    private static Connection connection;

    public static Connection getConnection()
    {
        return connection;
    }

    /**
     * Initializes the context.
     * 
     * Determines whether if the db is empty and if liquibase has ever been run or not. Meant to be run once at startup
     * 
     * @param connection from the main pool
     * @throws SQLException
     */
    static void init(Connection connection) throws SQLException
    {
        
        bAcceptSnapshotVersion=  AppPropertiesService.getPropertyBoolean(LIQUIBASE_ACCEPT_SNAPSHOT_VERSIONS, false);
        bAcceptUnstableVersion=  AppPropertiesService.getPropertyBoolean(LIQUIBASE_ACCEPT_UNSTABLE_VERSIONS, false);
        bEnabledDryRun=  AppPropertiesService.getPropertyBoolean("liquibase.dryrun", false);
        bEnableMigrationMode=  AppPropertiesService.getPropertyBoolean("liquibase.migration.mode", false);
        bSafeRun=  AppPropertiesService.getPropertyBoolean(LIQUIBASE_SAFE_RUN, true);
        LiquibaseRunnerContext.connection = connection;
        final String firstRunRequest = AppPropertiesService.getProperty(SQL__FIRST_LIQUIBASE_RUN_EVER, "select count(*) FROM information_schema.tables where table_name='DATABASECHANGELOG';");
        liquibaseNeverRan = runQuery(firstRunRequest, r -> r.getInt(1)) == 0;
        final String emptyDbRequest = AppPropertiesService.getProperty(SQL__EMPTY_DB, "SELECT count(*) FROM information_schema.tables where table_schema=database();");
        emptyDb = runQuery(emptyDbRequest, r -> r.getInt(1)) == 0;
        AppLogService.info("LiquibaseRunnerContext liquibaseNeverRan : {} , emptyDb : {}", liquibaseNeverRan, emptyDb);
    }

    /**
     * Runs all recorded tasks to set plugin versions.
     * 
     * Must be called when liquibase is done with the SQL files
     */
    static void close()
    {
     
      if(isEnableMigrationMode())
     {

        AppLogService.info("LiquibaseRunnerContext migration mode enabled update plugin version  in core datastore without running liquibase scripts");
         PluginMeta.getPluginsMeta().entrySet().stream().forEach(entry -> LiquibaseRunnerContext.setComponentVersion(entry.getKey(), entry.getValue(), false));

     }
     
     if(!bEnabledDryRun )
      {
          AppLogService.info(" updating datastore with plugins version and theme versions");
          entries.stream().forEach(entry -> DatastoreService.setDataValue(entry.key, entry.value));
     }
     else 
     {
         AppLogService.info("LiquibaseRunnerContext dry run enabled, not updating datastore with plugin versions");
     }

    }

    /**
     * 
     * Looks the component version in the DB.
     * 
     * "core" is a special case and always has a version :
     * 
     * Integer.MAX_VALUE if no true version can be found, which is newer than all other versions and ensures that no attempt to run any core scripts is
     * attempted when running liquibase for the first time on a existing DB.
     * 
    
     * @return the version as a string (for example "1.0.1"), or null if not found
     * @throws SQLException
     */
    public static PluginVersion componentVersion(SqlPathInfo info) throws SQLException
    {
        String version = DatastoreService.getDataValue(info.isTheme()?themeVersionKey(info.getTheme()):pluginVersionKey(info.getFullPluginName()), null);
        if (!info.isTheme()  && version == null && CORE_PLUGIN_NAME.equals(info.getPlugin()))
            version = "" + Integer.MAX_VALUE;
        return PluginVersion.of(version);
    }



    /**
     * Reports a SQL file whose path resolves to a component declared by no plugin descriptor
     * (nor themes.&lt;name&gt;.version property).
     *
     * This is a packaging fault (LUT-33232) : the file's directory and the shipping artifact's descriptor disagree.
     * When liquibase.safeRun is true (the default), startup is aborted before any changeset is executed
     * (this method is called while liquibase parses the changelog, before execution).
     * Otherwise the fault is only logged, and the caller excludes the file.
     *
     * @param path          the SQL file path
     * @param componentName the unresolved component name derived from the path
     */
    public static void reportUnresolvedComponent(String path, String componentName)
    {
        AppLogService.error("LiquibaseRunner. SQL file {} resolves to component '{}' which is not declared by any plugin descriptor : file NOT included", path,
                componentName);
        if (bSafeRun)
        {
            throw new IllegalStateException("LiquibaseRunner : SQL file " + path + " resolves to undeclared component '" + componentName
                    + "'. This is a packaging fault (see LUT-33232) : startup aborted because liquibase.safeRun=true. Set liquibase.safeRun=false to only exclude such files.");
        }
    }

    /**
     *
     * Looks the type of the last run script (create/init or update) in the DB.
     * 
     * 
     * @param pluginName a name such as 'forms'
     * @return the type of the last run script as a string ("create/init" or "update"), or null if not found
     * @throws SQLException
     */
    public static String  pluginLastRunScriptType(String pluginName) 
    {
        return DatastoreService.getDataValue(pluginLastRunScriptTypeKey(pluginName), null);
    }


    private static String pluginVersionKey(String pluginName)
    {
        return "core.plugins.status." + pluginName + ".version";
    }

    private static String themeVersionKey(String themeName)
    {
        return "core.theme.status." + themeName + ".version";
    }


    private static String pluginLastRunScriptTypeKey(String pluginName)
    {
        return "core.plugins.status." + pluginName + ".lastRunScriptType";
    }
       private static String themeLastRunScriptTypeKey(String themeName)
    {
        return "core.themes.status." + themeName + ".lastRunScriptType";
    }
    


    /**
     * Holds a key/value pair for later insert into the datastore
     */
    private static class DatastoreEntry
    {
        private final String key, value;

        public DatastoreEntry(String key, String value)
        {
            this.key = key;
            this.value = value;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(key);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            DatastoreEntry other = (DatastoreEntry) obj;
            return Objects.equals(key, other.key);
        }
    }

    private static final Set<DatastoreEntry> entries = new HashSet<>();

    /**
     * Sets the plugin version in the datastore (later, when all SQL files have been executed)
     * 
     * @param pluginName
     * @param version
     */
    public static void setComponentVersion(String componentName, String version, boolean isTheme)
    {
        entries.add(new DatastoreEntry(isTheme?themeVersionKey(componentName): pluginVersionKey(componentName), version));
    }


    /**
     * Sets the type of the last run script (create/init or update) in the datastore (later, when all SQL files have been executed)
     * 
     * @param pluginName
     * @param version
     */
    public static void setPluginLastRunScriptType(String pluginName, String strLastRunTypeScript)
    {
        entries.add(new DatastoreEntry(pluginLastRunScriptTypeKey(pluginName), strLastRunTypeScript));
    }

    /**
     * Sets the type of the last run script (create/init or update) in the datastore (later, when all SQL files have been executed)
     * 
     * @param pluginName
     * @param version
     */
    public static void setThemeLastRunScriptType(String themeName, String strLastRunTypeScript)
    {
        entries.add(new DatastoreEntry(themeLastRunScriptTypeKey(themeName), strLastRunTypeScript));
    }




    /**
     * Helper function to run queries and extract results.
     * 
     * As it is used early in the app's life, it avoids using existing classes such as DAOUtil which might have unwanted dependencies or behaviours.
     * 
     * @param <T>             result type
     * @param sql             the query
     * @param resultExtractor knows how to extract a T instance from a ResultSet
     * @return an instance, or null
     * @throws SQLException
     */
    private static <T> T runQuery(String sql, ResultSetExtractor<T> resultExtractor) throws SQLException
    {
        try (Statement firstRunStatement = connection.createStatement(); ResultSet result = firstRunStatement.executeQuery(sql);)
        {
            return result.next() ? resultExtractor.apply(result) : null;
        }

    }

    /** Because Function<ResultSet, T> does not throw */
    public interface ResultSetExtractor<T>
    {
        T apply(ResultSet result) throws SQLException;
    }

}
