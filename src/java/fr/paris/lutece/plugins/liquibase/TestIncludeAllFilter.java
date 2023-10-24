package fr.paris.lutece.plugins.liquibase;

import liquibase.changelog.IncludeAllFilter;

/**
 * Filter for use in the liquibase changelog config
 * This check is useless if we are absolutely sure that the source folder
 * only contains adequate SQL files
 */
public class TestIncludeAllFilter implements IncludeAllFilter {
	private static final String SQL_EXT = ".sql";

	@Override
	public boolean include(String changeLogPath) {
		// we check only the extension
		// no explicit check can be done here on the "file" represented by changeLogPath, since it might not be a file, but a classpath entry
		return changeLogPath.toLowerCase().endsWith(SQL_EXT);
	}
}
