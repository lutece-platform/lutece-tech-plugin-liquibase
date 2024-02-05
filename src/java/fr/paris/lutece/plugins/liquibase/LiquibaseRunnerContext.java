package fr.paris.lutece.plugins.liquibase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import fr.paris.lutece.plugins.liquibase.filters.PluginVersion;
import fr.paris.lutece.portal.service.datastore.DatastoreService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * Static context holder for shared data during a liquibase run.
 * 
 * This class offers a static API so that filters instantiated by liquibase can access its state.
 */
public class LiquibaseRunnerContext
{
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

    private static boolean liquibaseNeverRan, emptyDb;

    public static boolean isEmptyDb()
    {
        return emptyDb;
    }

    public static boolean isLiquibaseNeverRan()
    {
        return liquibaseNeverRan;
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
        entries.stream().forEach(entry -> DatastoreService.setDataValue(entry.key, entry.value));
    }

    /**
     * 
     * Looks the the plugin version in the DB.
     * 
     * "core" is a special case and always has a version :
     * 
     * Integer.MAX_VALUE if no true version can be found, which is newer than all other versions and ensures that no attempt to run any core scripts is
     * attempted when running liquibase for the first time on a existing DB.
     * 
     * @param pluginName a name such as 'forms'
     * @return the version as a string (for example "1.0.1"), or null if not found
     * @throws SQLException
     */
    public static PluginVersion pluginVersion(String pluginName) throws SQLException
    {
        String version = DatastoreService.getDataValue(pluginVersionKey(pluginName), null);
        if (version == null && CORE_PLUGIN_NAME.equals(pluginName))
            version = "" + Integer.MAX_VALUE;
        return PluginVersion.of(version);
    }

    private static String pluginVersionKey(String pluginName)
    {
        return "core.plugins.status." + pluginName + ".version";
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
    public static void setPluginVersion(String pluginName, String version)
    {
        entries.add(new DatastoreEntry(pluginVersionKey(pluginName), version));
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
