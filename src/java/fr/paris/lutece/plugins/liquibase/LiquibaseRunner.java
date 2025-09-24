package fr.paris.lutece.plugins.liquibase;

import java.io.InputStream;
import java.sql.Connection;

import fr.paris.lutece.portal.service.database.AppConnectionService;
import fr.paris.lutece.portal.service.init.IEarlyInitializationService;
import fr.paris.lutece.portal.service.util.AppLogService;
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
  

    @Override
    public void process()
    {
    
        // we do not run unless explicitly told to do so
        final boolean enabledAtStartup = AppPropertiesService.getPropertyBoolean(AT_STARTUP, false);
        if (!enabledAtStartup)
        {
            AppLogService.info("LiquibaseRunner not enabled at startup");
        } else
        {
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
                     System.setProperty("liquibase.analytics.enabled", "false");
                    try (Liquibase liquibase = new Liquibase("db/changelog.xml", new RegexpFilteringResourceAccessor(new ClassLoaderResourceAccessor(), helper),
                            database);)
                    {
                        LiquibaseRunnerContext.init(connection);
                        // neither the javadoc nor the tutorial are clear about an actual working replacement for update()
                        liquibase.update(new Contexts());
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
            }
            AppLogService.info("LiquibaseRunner ended");
        }
    }
}
