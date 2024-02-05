package fr.paris.lutece.plugins.liquibase;

import java.sql.Connection;

import fr.paris.lutece.plugins.liquibase.filters.PluginVersion;
import fr.paris.lutece.portal.service.database.AppConnectionService;
import fr.paris.lutece.portal.service.init.IEarlyInitializationService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Entry point for plugin early run
 */
public class LiquibaseRunner implements IEarlyInitializationService
{
    private static final String AT_STARTUP = "liquibase.enabled.at.startup";
    private static final String LIQUIBASE_ACCEPT_SNAPSHOT_VERSIONS = "liquibase.accept.snapshot.versions";

    @Override
    public void process()
    {
        PluginVersion.setAcceptSnapshots(AppPropertiesService.getPropertyBoolean(LIQUIBASE_ACCEPT_SNAPSHOT_VERSIONS, false));
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
                // Here we get a default connection from the default pool
                // TODO (or not) :
                // - loop on pools
                // - determine which SQL scripts are for these pools
                // - call liquibase for each pool (OR use liquibase support for multi-DB)
                try (Connection connection = AppConnectionService.getConnection();
                        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                        Liquibase liquibase = new Liquibase("db/changelog.xml", new ClassLoaderResourceAccessor(), database);)
                {
                    LiquibaseRunnerContext.init(connection);
                    // neither the javadoc nor the tutorial are clear about an actual working replacement for update()
                    liquibase.update(new Contexts());
                    // closing the context bumps plugin versions in the datastore
                    // only if all went as planned
                    LiquibaseRunnerContext.close();
                }
            } catch (Throwable e)
            {
                AppLogService.error("LiquibaseRunner failed", e);
            }
            AppLogService.info("LiquibaseRunner ended");
        }
    }
}
