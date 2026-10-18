package fr.paris.lutece.plugins.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The SQL mode of the session of the pooled connection used by liquibase is restored after the run (a script such as init_db_unittree.sql sets
 * SET SESSION sql_mode='NO_AUTO_VALUE_ON_ZERO', and every later INSERT ... VALUES( DEFAULT ) on that connection then stored 0).
 */
public class SessionSqlModeTest
{
    private static final String MODE = "STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION";

    private final List<String> _listSql = new ArrayList<>( );
    private final List<Object> _listParameters = new ArrayList<>( );

    @Test
    public void testRestoredOnMariaDB( ) throws Exception
    {
        Connection connection = connection( "jdbc:mariadb://db:3306/lutece" );

        try ( LiquibaseRunner.SessionSqlMode mode = new LiquibaseRunner.SessionSqlMode( connection ) )
        {
            // a script changes the SQL mode of the session
            _listSql.add( "SET SESSION sql_mode='NO_AUTO_VALUE_ON_ZERO'" );
        }

        assertEquals( "SELECT @@SESSION.sql_mode", _listSql.get( 0 ) );
        assertEquals( "SET SESSION sql_mode = ?", _listSql.get( _listSql.size( ) - 1 ) );
        assertEquals( MODE, _listParameters.get( 0 ) );
    }

    @Test
    public void testNothingOnOtherDatabases( ) throws Exception
    {
        Connection connection = connection( "jdbc:postgresql://db:5432/lutece" );

        try ( LiquibaseRunner.SessionSqlMode mode = new LiquibaseRunner.SessionSqlMode( connection ) )
        {
            // nothing
        }

        assertTrue( _listSql.isEmpty( ) );
    }

    private Connection connection( String strUrl )
    {
        DatabaseMetaData metaData = proxy( DatabaseMetaData.class, ( p, m, a ) -> "getURL".equals( m.getName( ) ) ? strUrl : null );
        ResultSet resultSet = proxy( ResultSet.class, new java.lang.reflect.InvocationHandler( )
        {
            private boolean _bRead;

            @Override
            public Object invoke( Object p, java.lang.reflect.Method m, Object [ ] a )
            {
                switch( m.getName( ) )
                {
                    case "next":
                        boolean bNext = !_bRead;
                        _bRead = true;
                        return bNext;
                    case "getString":
                        return MODE;
                    default:
                        return null;
                }
            }
        } );
        Statement statement = proxy( Statement.class, ( p, m, a ) -> {
            if ( "executeQuery".equals( m.getName( ) ) )
            {
                _listSql.add( (String) a [0] );
                return resultSet;
            }
            return null;
        } );
        return proxy( Connection.class, ( p, m, a ) -> {
            switch( m.getName( ) )
            {
                case "getMetaData":
                    return metaData;
                case "createStatement":
                    return statement;
                case "prepareStatement":
                    _listSql.add( (String) a [0] );
                    return proxy( PreparedStatement.class, ( ps, pm, pa ) -> {
                        if ( "setString".equals( pm.getName( ) ) )
                        {
                            _listParameters.add( pa [1] );
                        }
                        return "execute".equals( pm.getName( ) ) ? Boolean.FALSE : null;
                    } );
                default:
                    return null;
            }
        } );
    }

    @SuppressWarnings( "unchecked" )
    private static <T> T proxy( Class<T> clazz, java.lang.reflect.InvocationHandler handler )
    {
        return (T) Proxy.newProxyInstance( SessionSqlModeTest.class.getClassLoader( ), new Class<?> [ ] {
                clazz
        }, handler );
    }
}
