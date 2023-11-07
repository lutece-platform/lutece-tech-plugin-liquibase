package fr.paris.lutece.plugins.liquibase;

import org.springframework.stereotype.Component;

import fr.paris.lutece.portal.service.init.StartUpService;
import fr.paris.lutece.portal.service.util.AppLogService;
@Component
public class LiquibaseOnDemandRunner implements StartUpService
{
    @Override
    public String getName()
    {
        return "LiquibaseOnDemand";
    }

    @Override
    public void process()
    {
        AppLogService.info("LiquibaseOnDemandRunner process starting");
    }
}
