package fr.paris.lutece.plugins.liquibase;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import fr.paris.lutece.portal.service.database.AppConnectionService;
import fr.paris.lutece.portal.service.init.IEarlyInitializationService;
import fr.paris.lutece.portal.service.init.LuteceInitException;
import fr.paris.lutece.plugins.liquibase.filters.PrerunScripts;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.utils.sql.PluginVersion;
import fr.paris.lutece.utils.sql.SqlRegexpHelper;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Entry point for plugin early run
 */
public class LiquibaseRunner implements IEarlyInitializationService
{
    private static final String AT_STARTUP = "liquibase.enabled.at.startup";
    private static final String DRY_RUN_OUTPUT_FILE = "liquibase.dryrun.output.file";
    private static final String DRY_RUN = "liquibase.dryrun";
    private static final String READY_TO_RUN = "liquibase.readyToRun";
    private static final String ANALYTICS_ENABLED = "liquibase.analytics.enabled";
    private static final String SQL_LOG_LEVEL = "liquibase.sql.logLevel";
    private static final String SAFE_RUN = "liquibase.safeRun";
    private static final String LIQUIBASE_FILE_ERRORS="liquibase.fileErrors";
    private static final String FAIL_ON_ERROR = "liquibase.failOnError";
    private static final String SQL_SELECT_SESSION_SQL_MODE = "SELECT @@SESSION.sql_mode";
    private static final String SQL_SET_SESSION_SQL_MODE = "SET SESSION sql_mode = ?";

    @Override
    public void process()
    {
    
        // we do not run unless explicitly told to do so
        final boolean enabledAtStartup = AppPropertiesService.getPropertyBoolean(AT_STARTUP, false);
        final boolean enabledDryRun = AppPropertiesService.getPropertyBoolean(DRY_RUN, false);
        final boolean safeRunEnabled = AppPropertiesService.getPropertyBoolean(SAFE_RUN, true);
        //property generated during the build process in the file WEB-INF/classes/sql/microprofile-config.properties
        final boolean readyToRun = AppPropertiesService.getPropertyBoolean(READY_TO_RUN, true);
        final String liquibaseFileErrors=AppPropertiesService.getProperty(LIQUIBASE_FILE_ERRORS,"");


        if (!enabledAtStartup)
        {
            AppLogService.info("LiquibaseRunner not enabled at startup");
        }else if ( !readyToRun  && safeRunEnabled )
        {
            AppLogService.error("LiquibaseRunner not ready to run stopping startup process");
            AppLogService.error("LiquibaseRunner files not managed by liquibase are {}" ,liquibaseFileErrors );
            throw new RuntimeException("LiquibaseRunner not ready to run stopping startup process");
        }
        else
        {
            if(!readyToRun)
            {
                AppLogService.error("LiquibaseRunner is not ready to run, but safe run is disabled, proceeding with liquibase execution");
                AppLogService.error("LiquibaseRunner files not managed by liquibase are {}" ,liquibaseFileErrors );
            }

            AppLogService.info("LiquibaseRunner starting");
            try
            {
                PluginMeta.preloadMeta();// load plugin versions from XML files
                // Here we get a default connection from the default pool
                // TODO (or not) :
                // - loop on pools
                // - determine which SQL scripts are for these pools
                // - call liquibase for each pool (OR use liquibase support for multi-DB)
                boolean allWentWell = false;
                try (Connection connection = AppConnectionService.getConnection();
                        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));)
                {
                    InputStream buildProperties = getClass().getResourceAsStream("/sql/build.properties");
                    SqlRegexpHelper helper = null;
                    // build.properties is present only if the war was built without SQL processing
                    // so, we process
                    if (buildProperties != null)
                    {
                        String url = database.getConnection().getURL();
                        AppLogService.info("LiquibaseRunner. Determining target database from connection URL : " + url);
                        helper = new SqlRegexpHelper(() -> buildProperties, SqlRegexpHelper.findDbName(url));
                    }
                    //System.setProperty("liquibase.shouldSendAnalytics", "false");
                     System.setProperty("liquibase.analytics.enabled", AppPropertiesService.getProperty(ANALYTICS_ENABLED, "false"));
                     System.setProperty("liquibase.sql.logLevel", AppPropertiesService.getProperty(SQL_LOG_LEVEL, "DEBUG"));

                    boolean forceFailOnErrorFalse = !AppPropertiesService.getPropertyBoolean(FAIL_ON_ERROR, true);
                    RegexpFilteringResourceAccessor resourceAccessor = new RegexpFilteringResourceAccessor(new ClassLoaderResourceAccessor(), helper, forceFailOnErrorFalse);
                    // The connection comes from the pool of the application and goes back to it: the SQL mode of its session is restored
                    // before liquibase closes the connection, a script that changes it (SET SESSION sql_mode='NO_AUTO_VALUE_ON_ZERO')
                    // would change every later SQL run on it
                    try (Liquibase liquibase = new Liquibase("db/changelog.xml", resourceAccessor, database);
                            SessionSqlMode sessionSqlMode = new SessionSqlMode(connection);)
                    {
                        LiquibaseRunnerContext.init(connection);
                        // neither the javadoc nor the tutorial are clear about an actual working replacement for update()
                        
                        if( enabledDryRun )
                        {
                            String dryRunOutputFile = AppPropertiesService.getProperty(DRY_RUN_OUTPUT_FILE, "WEB-INF/plugins/liquibase/liquibase-dryrun.sql");

                            try (FileOutputStream fos = new FileOutputStream( dryRunOutputFile.startsWith("/") ? dryRunOutputFile : AppPathService.getAbsolutePathFromRelativePath("/" + dryRunOutputFile), false);
                                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                                 )
                            {
                                AppLogService.info("LiquibaseRunner running in dry run mode. Output file : " + dryRunOutputFile);
                                runPrerunScripts(connection, resourceAccessor, writer);
                                liquibase.update(new Contexts(), writer);
                            }
                            
                        }
                        else
                        {
                            AppLogService.info("LiquibaseRunner applying database changes");
                            AppLogService.info("LiquibaseRunner enable migration mode   : " + LiquibaseRunnerContext.isEnableMigrationMode() );
                            runPrerunScripts(connection, resourceAccessor, null);
                            liquibase.update(new Contexts());
                        }
                        // closing the context bumps plugin versions in the datastore
                        // only if all went as planned
                        LiquibaseRunnerContext.close();
                        allWentWell = true;
                    }
                } catch (DatabaseException dbe)
                {
                    // special case with some pools (tomcat for instance)
                    // somehow the close() method of the connection might have already been called
                    // when the automatic Database.close() (AbstractJdbcDatabase actually) calls connection.setAutoCommit()
                    // which causes the auto-close to fail
                    // Thus we throw only if something REALLY failed.
                    if (!allWentWell)
                        throw dbe;
                }
            } catch (Throwable e)
            {
                AppLogService.error("LiquibaseRunner failed", e);
                throw new RuntimeException("LiquibaseRunner failed stopping startup process");
            }
            AppLogService.info("LiquibaseRunner ended");
        }
    }

    /**
     * Executes the reserved pre-execution scripts (prerun_db_&lt;component&gt;.sql) in a preliminary liquibase
     * update, BEFORE the main changelog is built and filtered (LUT-33326) : the main run's version
     * resolution then sees the state they leave behind (e.g. datastore keys and DATABASECHANGELOG paths
     * migrated after a plugin rename). Skipped in migration mode, like every script of the main changelog.
     *
     * A dedicated Database instance wraps the same JDBC connection so that liquibase services caching
     * per-database state (ran changesets, locks) do not leak into the main run ; neither the Liquibase
     * object nor that Database are closed here, closing them would close the shared connection still
     * needed by the main run.
     */
    private void runPrerunScripts(Connection connection, RegexpFilteringResourceAccessor resourceAccessor, Writer dryRunWriter) throws Exception
    {
        if (LiquibaseRunnerContext.isEnableMigrationMode() || !PrerunScripts.any())
        {
            return;
        }
        AppLogService.info("LiquibaseRunner executing prerun_db scripts before the main changelog");
        Database preDatabase = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase preliminary = new Liquibase("db/changelog-pre.xml", resourceAccessor, preDatabase);
        if (dryRunWriter != null)
        {
            preliminary.update(new Contexts(), dryRunWriter);
        }
        else
        {
            preliminary.update(new Contexts());
        }
    }

    /**
     * SQL mode of the session of a connection, restored when closed
     */
    static final class SessionSqlMode implements AutoCloseable
    {
        private final Connection _connection;
        private final String _strSqlMode;

        /**
         * Reads the SQL mode of the session
         *
         * @param connection
         *            the connection
         */
        SessionSqlMode(Connection connection)
        {
            _connection = connection;
            _strSqlMode = readSessionSqlMode(connection);
        }

        /**
         * Restores the SQL mode of the session
         */
        @Override
        public void close()
        {
            restoreSessionSqlMode(_connection, _strSqlMode);
        }
    }

    /**
     * Reads the SQL mode of the session of a MySQL or MariaDB connection
     *
     * @param connection
     *            the connection
     * @return the SQL mode, or null for another database or if it cannot be read
     */
    static String readSessionSqlMode(Connection connection)
    {
        try
        {
            String strUrl = connection.getMetaData().getURL();
            if (strUrl == null || !(strUrl.startsWith("jdbc:mysql:") || strUrl.startsWith("jdbc:mariadb:")))
            {
                return null;
            }
            try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(SQL_SELECT_SESSION_SQL_MODE))
            {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
        catch (SQLException e)
        {
            AppLogService.error("LiquibaseRunner could not read the SQL mode of the session", e);
            return null;
        }
    }

    /**
     * Restores the SQL mode of the session of a MySQL or MariaDB connection, before it goes back to the pool
     *
     * @param connection
     *            the connection
     * @param strSqlMode
     *            the SQL mode read before the run, null to do nothing
     */
    static void restoreSessionSqlMode(Connection connection, String strSqlMode)
    {
        if (strSqlMode == null)
        {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL_SET_SESSION_SQL_MODE))
        {
            statement.setString(1, strSqlMode);
            statement.execute();
        }
        catch (SQLException e)
        {
            AppLogService.error("LiquibaseRunner could not restore the SQL mode of the session", e);
        }
    }

   @Override   
   public boolean isCriticalService()
    {
        return true;
    }
}
