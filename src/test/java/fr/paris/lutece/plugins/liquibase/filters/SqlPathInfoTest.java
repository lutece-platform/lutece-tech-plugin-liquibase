package fr.paris.lutece.plugins.liquibase.filters;

import junit.framework.TestCase;

public class SqlPathInfoTest extends TestCase
{
    public static void main(String[] args)
    {
        final String[] files = new String[] { "sql/plugins/testpourliquibase/plugin/create_db_testpourliquibase.sql",
                "sql/plugins/testpourliquibase/upgrade/update_db_testpourliquibase-0.0.9-1.0.0.sql", "sql/upgrade/update_db_lutece_core-2.3.0-2.3.1.sql",
                "sql/init_db_lutece_core.sql", "sql/plugins/regularexpression/upgrade/update_db_regularexpression_3.0.0_3.0.1.sql",
                "sql/upgrade/update_db_lutece_core-7.0.9-7.0.10.sql",
                "sql/plugins/bignumberplugin/upgrade/update_db_whatever-654.123.789-78999.6546546.321321321.sql",
                "sql/plugins/forms/modules/template/plugin/create_db_forms-template.sql",
                "sql/plugins/contact/upgrades/update_db_contact-2.0.12-2.0.13.sql",
                "sql/plugins/profiles/upgrades/upgrade_db_profiles-1.0.3-1.0.4.sql",
                "sql/plugins/workflow/modules/formspdf/upgrade/update_db_workflow-formspdf-1.0.0-1.0.1.sql",
                "sql/plugins/elasticdata-forms/plugin/create_db_elasticdata-forms.sql",
                "sql/plugins/forms/upgrade/update_db_forms-2.3.0-2.3.1.sql",
                "sql/plugins/forms/modules/template/upgrade/update_db_forms_template-1.0.2-1.0.4.sql"};
        for (String file : files)
        {
            System.out.println("file : " + file);
            SqlPathInfo info = SqlPathInfo.parse(file);
            System.out.println("    info : " + info);
        }
    }

    public void testPluginCreate()
    {
        SqlPathInfo info = SqlPathInfo.parse("sql/plugins/testpourliquibase/plugin/create_db_testpourliquibase.sql");
        assertEquals(info.isCreate(), true);
        assertEquals(info.getPlugin(), "testpourliquibase");
    }

    public void testUpdateCreate()
    {
        PluginVersion v1 = PluginVersion.of("1.0.0");
        SqlPathInfo info = SqlPathInfo.parse("sql/plugins/testpourliquibase/upgrade/update_db_testpourliquibase-0.0.9-1.0.1.sql");
        assertEquals(info.isCreate(), false);
        assertTrue(info.getSrcVersion().compareTo(v1) < 0);
        assertTrue(info.getDstVersion().compareTo(v1) > 0);
    }

    public void testBig()
    {
        SqlPathInfo info = SqlPathInfo.parse("sql/plugins/bignumberplugin/upgrade/update_db_whatever-654.123.789-78999.6546546.321321321.sql");
        assertEquals(info.isCreate(), false);
        assertTrue(info.getDstVersion().components().get(2) == 321321321);
    }

    public void testModule()
    {
        SqlPathInfo info = SqlPathInfo.parse("sql/plugins/forms/modules/template/upgrade/update_db_forms_template-1.0.2-1.0.4.sql");
        assertEquals(info.isCreate(), false);
        assertEquals("forms", info.getPlugin());
        assertEquals("forms-template", info.getFullPluginName());
    }

    public void testUpdateCore()
    {
        SqlPathInfo info = SqlPathInfo.parse("sql/upgrade/update_db_lutece_core-7.0.9-7.0.10.sql");
        assertEquals(info.isCreate(), false);
        assertEquals(info.getPlugin(), "core");
        assertTrue(info.getDstVersion().compareTo(info.getSrcVersion()) > 0);
    }

    public void testCreateCore()
    {
        SqlPathInfo info = SqlPathInfo.parse("sql/init_db_lutece_core.sql");
        assertEquals(info.isCreate(), true);
        assertEquals(info.getPlugin(), "core");
    }
}