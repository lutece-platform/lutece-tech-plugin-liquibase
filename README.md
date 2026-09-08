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


# Pre-execution scripts: the reserved prerun_db file

Some one-shot fix-ups must be visible to the version resolution of the main liquibase run, or they are useless. Typical case, a plugin renamed between two releases: its SQL directory changes, so on an existing site the new component has no version in the datastore — it is treated as a fresh install, its update scripts are discarded, and the recorded version then jumps to the current release, so the discarded updates are never applied again. A migration script cannot help as long as it runs inside the main update: the inclusion decisions are already made when it executes.

A plugin may ship **one reserved liquibase formatted SQL file per component**, at a fixed path derived from the component name:

```
sql/plugins/<plugin>/plugin/prerun_db_<plugin>.sql
sql/plugins/<plugin>/modules/<module>/plugin/prerun_db_<plugin>-<module>.sql
```

All `prerun_db_*` files are executed in a preliminary liquibase update, before the main changelog is built and filtered, and are excluded from the main run. Since a formatted SQL file may hold several changesets, this file is the component's pre-execution mini-changelog: each release needing a fix-up appends a changeset to the same file. Changesets are recorded in `DATABASECHANGELOG`, run once, and support preconditions and `validCheckSum`.

Example — after the rename of a plugin from `oldname` to `newname`, `sql/plugins/newname/plugin/prerun_db_newname.sql`:

```sql
-- liquibase formatted sql
-- changeset newname:prerun-rename-oldname
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:1 SELECT COUNT(DISTINCT 1) FROM DATABASECHANGELOG WHERE FILENAME LIKE 'sql/plugins/oldname/%'
UPDATE core_datastore SET entity_key = REPLACE(entity_key,'core.plugins.status.oldname.','core.plugins.status.newname.') WHERE entity_key LIKE '%core.plugins.status.oldname.%';
UPDATE DATABASECHANGELOG SET FILENAME = REPLACE(FILENAME,'sql/plugins/oldname/','sql/plugins/newname/') WHERE FILENAME LIKE 'sql/plugins/oldname/%';
```

The installed version then survives the rename: the main run sees it and includes the proper upgrade scripts, and the already-executed changesets keep their identity under the new paths.

Authoring rules:

* Guard every changeset with preconditions failing to `MARK_RAN` both `onFail` and `onError`: the file runs on every database state, including an empty one at first install, where the precondition query itself may fail.
* Never modify the body of an executed changeset without declaring `validCheckSum:`.
* A fix-up already executed under another path on existing sites must, when moved into the reserved file, declare its original identity with `logicalFilePath:` on its changeset line.
* Execution order between `prerun_db_*` files is alphabetical by path; `runAfter` does not apply to the preliminary run, which is also skipped in migration mode.
* A `prerun_db_*` file at an unexpected location, misnamed, or owned by an undeclared component is a packaging fault, reported like an unresolved component (startup aborted when `liquibase.safeRun=true`).


[Maven documentation and reports](https://dev.lutece.paris.fr/plugins/plugin-liquibase/)



 *generated by [xdoc2md](https://github.com/lutece-platform/tools-maven-xdoc2md-plugin) - do not edit directly.*