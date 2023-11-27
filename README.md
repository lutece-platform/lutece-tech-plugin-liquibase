![](https://dev.lutece.paris.fr/jenkins/buildStatus/icon?job=plugin-liquibase-deploy)
[![Alerte](https://dev.lutece.paris.fr/sonar/api/project_badges/measure?project=fr.paris.lutece.plugins%3Aplugin-liquibase&metric=alert_status)](https://dev.lutece.paris.fr/sonar/dashboard?id=fr.paris.lutece.plugins%3Aplugin-liquibase)
[![Line of code](https://dev.lutece.paris.fr/sonar/api/project_badges/measure?project=fr.paris.lutece.plugins%3Aplugin-liquibase&metric=ncloc)](https://dev.lutece.paris.fr/sonar/dashboard?id=fr.paris.lutece.plugins%3Aplugin-liquibase)
[![Coverage](https://dev.lutece.paris.fr/sonar/api/project_badges/measure?project=fr.paris.lutece.plugins%3Aplugin-liquibase&metric=coverage)](https://dev.lutece.paris.fr/sonar/dashboard?id=fr.paris.lutece.plugins%3Aplugin-liquibase)

# Introduction

This plugin allows for automatic execution of SQL scripts at application startup. Several modes are supported :
 
* Creation : if the database is empty, all create and init scripts will be run.
* Migration : if this plugin was not in use in the previous version, the (previously non empty) database will be configured to use the plugin.
* Update : newly detected scripts will be run.


# Configuration

By default the plugin does nothing.

You can declare the following property to enable the plugin: `liquibase.enabled.at.startup=true` 

Additionally you can use this property to allow SNAPSHOT versions `liquibase.accept.snapshot.versions=false` 

# Usage

This example demonstrates the necessary steps to automatically have liquibase run SQL scripts from your WAR for a new plugin at tomcat startup.
 
* Create a sample plugin with the needed dependencies
* Run the liquibase tool to prepare the SQL scripts for automatic run
* Integrate this plugin in an existing app
* Run


## Create a sample plugin

Use the plugin wizard to generate a new plugin (https://dev.lutece.paris.fr/tools/jsp/site/Portal.jsp?page=pluginwizard).

``` xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

    <parent>
        <artifactId>lutece-global-pom</artifactId>
        <groupId>fr.paris.lutece.tools</groupId>
        <version>6.1.0</version>
    </parent>

    <modelVersion>4.0.0</modelVersion>
    <groupId>fr.paris.lutece.plugins</groupId>

    <artifactId>plugin-testpourliquibase</artifactId>
    <packaging>lutece-plugin</packaging>
    
    <version>1.0.0-SNAPSHOT</version>
    <name>Lutece testpourliquibase plugin</name>

    <repositories>
        <repository>
            <id>lutece</id>
            <name>luteceRepository</name>
            <url>https://dev.lutece.paris.fr/maven_repository</url>
            <layout>default</layout>
        </repository>
        <repository>
            <id>luteceSnapshot</id>
            <name>luteceSnapshot</name>
            <url>https://dev.lutece.paris.fr/snapshot_repository</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
            <releases>
                <enabled>false</enabled>
            </releases>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>fr.paris.lutece</groupId>
            <artifactId>lutece-core</artifactId>
            <version>7.0.11-SNAPSHOT</version>
            <type>lutece-core</type>
        </dependency>
    </dependencies>

    <properties>
        <componentName>testpourliquibase</componentName>
        <jiraProjectName>TESTPOURLIQUIBASE</jiraProjectName>
        <jiraComponentId></jiraComponentId>
    </properties>
</project>


```

At the time of writing, the importants parts to make this pom.xml example work are
 
* version 6.1.0 for the parent
* version 7.0.11-SNAPSHOT for lutece-core


## Run the liquibase tool

If you used the plugin wizard, your plugin source code has some SQL scripts ( containing one "create table", or more).

Now we need, at development time, to tag these scripts with some special comments that liquibase will recognize at run time. A goal is provided by the lutece maven tools plugin to automatically generate those tags.
 `mvn fr.paris.lutece.tools:lutece-maven-plugin:4.1.3-SNAPSHOT:liquibase-sql` 
This command walks through all your SQL source files in src/sql and generates corresponding tagged files in src/liquibasesql. All SQL files are tagged, with no pre-condition. The liquibase plugin at run-time will determine whether a particular file needs to be run

By default all SQL files are replaced in the folder src/sql. If you want to do a dry run (to folder target/liquibasesql), you may set the **dryRun** parameter to true on the command line.

You might then want to build your plugin with `mvn clean install` to make it available to the build of your web site.

## Integrate the plugin

If you do not have a lutece web site suitable for testing, please create one as usual. Then add these two dependencies (the liquibase plugin, and your own plugin) to the pom.xml of your web site :

``` xml
        <dependency>
        	<groupId>fr.paris.lutece.plugins</groupId>
            <artifactId>plugin-liquibase</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>lutece-plugin</type>
        </dependency>
        <dependency>
        	<groupId>fr.paris.lutece.plugins</groupId>
            <artifactId>plugin-testpourliquibase</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>lutece-plugin</type>
        </dependency>

```

The version number for **plugin-testpourliquibase** is the one you used when creating a test plugin above.

The default behaviour of the liquibase plugin when starting a war is to do nothing. To enable it, you must define the following property in the properties file **liquibase-plugin.properties** (full relative path with **dev** profile : ./src/conf/dev/WEB-INF/conf/plugins/liquibase-plugin.properties) : `liquibase.enabled.at.startup=true` Then build your site `mvn clean fr.paris.lutece.tools:lutece-maven-plugin:4.1.3-SNAPSHOT:site-assembly -P dev` This command uses the **dev** profile and, importantly, specifies the version of the plugin, which places the SQL in the right folder so that liquibase can find them at run time in the classpath.

## Run

Deploy your site as usual in tomcat.

The liquibase plugin will output traces for its activity at startup between lines "LiquibaseRunner starting" and "LiquibaseRunner ended".


[Maven documentation and reports](https://dev.lutece.paris.fr/plugins/plugin-liquibase/)



 *generated by [xdoc2md](https://github.com/lutece-platform/tools-maven-xdoc2md-plugin) - do not edit directly.*