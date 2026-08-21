![](https://dev.lutece.paris.fr/jenkins/buildStatus/icon?job=tech-plugin-liquibase-deploy)
[![Alerte](https://dev.lutece.paris.fr/sonar/api/project_badges/measure?project=fr.paris.lutece.plugins%3Aplugin-liquibase&metric=alert_status)](https://dev.lutece.paris.fr/sonar/dashboard?id=fr.paris.lutece.plugins%3Aplugin-liquibase)
[![Line of code](https://dev.lutece.paris.fr/sonar/api/project_badges/measure?project=fr.paris.lutece.plugins%3Aplugin-liquibase&metric=ncloc)](https://dev.lutece.paris.fr/sonar/dashboard?id=fr.paris.lutece.plugins%3Aplugin-liquibase)
[![Coverage](https://dev.lutece.paris.fr/sonar/api/project_badges/measure?project=fr.paris.lutece.plugins%3Aplugin-liquibase&metric=coverage)](https://dev.lutece.paris.fr/sonar/dashboard?id=fr.paris.lutece.plugins%3Aplugin-liquibase)

# Introduction

This plugin allows for automatic execution of SQL scripts at application startup. Several modes are supported :
 
* Creation : if the database is empty, all create and init scripts will be run.
* Migration : if this plugin was not in use in the previous version, the (previously non empty) database will be configured to use the plugin.
* Update : newly detected scripts will be run.


# Configuration

By default the plugin does nothing. All configurations are defined in the `liquibase-plugin.properties` file.

## General Properties

| Property| Description| Default Value|
|-----------------|-----------------|-----------------|
|  `liquibase.enabled.at.startup` | Enable the plugin execution at application startup| false|
|  `liquibase.safeRun` | Force liquibase to run without checking if existing files are managed by liquibase (false to bypass safety check)| true|
|  `liquibase.first.run.request` | SQL query to check if this is the first run (checks for DATABASECHANGELOG table)| select count(*) FROM information_schema.tables where table_name='DATABASECHANGELOG';|
|  `liquibase.empty.db.request` | SQL query to check if the database is empty| SELECT count(*) FROM information_schema.tables where table_schema=database();|

## Version Control

| Property| Description| Default Value|
|-----------------|-----------------|-----------------|
|  `liquibase.accept.unstable.versions` | Allow running liquibase on update files of unstable versions (alpha, beta, rc) at every startup| false|
|  `liquibase.accept.snapshot.versions` | Allow running liquibase on update files of snapshot versions at every startup| false|

## Dry Run Mode

| Property| Description| Default Value|
|-----------------|-----------------|-----------------|
|  `liquibase.dryrun` | Enable dry run mode to output SQL script instead of applying changes to the database| false|
|  `liquibase.dryrun.output.file` | Path to the output file for dry run SQL script| WEB-INF/plugins/liquibase/liquibase-dryrun.sql|

## Analytics and Logging

| Property| Description| Default Value|
|-----------------|-----------------|-----------------|
|  `liquibase.analytics.enabled` | Enable sending analytics to Liquibase| false|
|  `liquibase.sql.logLevel` | Set log level for SQL output (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL, DEBUG)| DEBUG|

## Migration Mode

| Property| Description| Default Value|
|-----------------|-----------------|-----------------|
|  `liquibase.migration.mode` | Enable migration mode to update plugin versions in datastore without applying liquibase changesets| false|

## Error Handling

| Property| Description| Default Value|
|-----------------|-----------------|-----------------|
|  `liquibase.failOnError` | Stop on SQL error in a changeset (true) or continue with the next changesets (false)| true|


# Script ordering: the runAfter header directive

By default, liquibase executes the SQL files in alphabetical path order. When a plugin needs its scripts to run after those of another plugin (typically to insert rows into tables created by that other plugin), the historical workaround was to ship the SQL file directly in the other plugin's directory. This practice breaks version resolution: the inclusion of the file is then decided against the other plugin's version, never the owner's.

The `runAfter` header directive replaces this practice. Declare it in the leading comment block (before the first changeset) of any script of the plugin:

```sql
--liquibase formatted sql
--lutece runAfter:genericattributes
--changeset forms:init_db_generic_attributes_forms.sql
INSERT INTO genatt_entry_type (id_type, title, ...) VALUES (1, 'Radio button', ...);
```

All the scripts of the declaring plugin (here `forms`) are then ordered after all the scripts of the target plugin (here `genericattributes`), while the files physically stay in their owner's directory — so version resolution remains correct.

* The directive is plugin-scoped: one declaration in a single file is enough for the whole plugin.
* Directives chain to any depth: if A declares runAfter:B and B declares runAfter:C, the resulting order is C, then B, then A.
* Invalid directives (conflicting targets inside one plugin, unknown or script-less target, self reference, involvement of core, dependency cycles) are ignored with an ERROR log and the plugin keeps its natural position.


[Maven documentation and reports](https://dev.lutece.paris.fr/plugins/plugin-liquibase/)



 *generated by [xdoc2md](https://github.com/lutece-platform/tools-maven-xdoc2md-plugin) - do not edit directly.*