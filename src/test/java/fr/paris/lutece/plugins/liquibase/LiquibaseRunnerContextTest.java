package fr.paris.lutece.plugins.liquibase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * Validates the table lookup deciding whether liquibase ever ran on the database and whether the database is empty.
 */
public class LiquibaseRunnerContextTest
{
    /**
     * Only the schema of the connection counts, whatever the case the table name is written in.
     *
     * @throws SQLException if the in-memory database fails
     */
    @Test
    public void lookupIsScopedToTheSchemaOfTheConnection() throws SQLException
    {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:liquibase-context", "SA", "");
                Statement statement = connection.createStatement())
        {
            statement.execute("CREATE SCHEMA othersite");
            statement.execute("CREATE TABLE othersite.DATABASECHANGELOG (id INT)");
            assertFalse(LiquibaseRunnerContext.hasTable(connection, "DATABASECHANGELOG"), "the changelog table of another schema is ignored");
            assertFalse(LiquibaseRunnerContext.hasTable(connection, "%"), "the tables of another schema do not make the database non empty");

            statement.execute("CREATE TABLE DATABASECHANGELOG (id INT)");
            assertTrue(LiquibaseRunnerContext.hasTable(connection, "DATABASECHANGELOG"));
            assertTrue(LiquibaseRunnerContext.hasTable(connection, "databasechangelog"), "the name is looked up in the case the database stores");
            assertTrue(LiquibaseRunnerContext.hasTable(connection, "%"));
        }
    }
}
