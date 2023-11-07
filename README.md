# Liquibase Plugin

## Usage example

This example demonstrates the necessary steps to automatically have liquibase run SQL scripts from your WAR at tomcat startup.
 - Create a sample plugin with the needed dependencies
 - Run the liquibase tool to prepare the SQL scripts for automatic run
 - Integrate this plugin in an existing app
 - Run

 ### Create a sample plugin

 Use the plugin wizard to generate a new plugin (https://dev.lutece.paris.fr/tools/jsp/site/Portal.jsp?page=pluginwizard).
 
```xml
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
            <version>7.0.10-SNAPSHOT</version>
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
 - version 6.1.0 for the parent
 - version 7.0.10-SNAPSHOT for lutece-core


### Run the liquibase tool

If you used the plugin wizard, your plugin source code has some SQL scripts ( containing one "create table", or more).

Now we need, at development time, to tag these scripts with some special comments that liquibase will recognize at run time.
A goal is provided by the lutece maven tools plugin to automatically generate those tags.

> mvn fr.paris.lutece.tools:lutece-maven-plugin:4.1.3-SNAPSHOT:liquibase-sql

The command above uses the fully qualified name of the plugin and supposes for now that you have built it from source (branch LUT-TODO-put-the-branch-name-here-once-its-merged) on your development environment.
This can be shortened to
> mvn lutece:liquibase-sql

This command walks through all your SQL source files in src/sql and generates corresponding tagged files in src/liquibasesql. It does its best to auto-determine which liquibase checks suit your files, but its capabilities are limited, so it might fail for some files.

Run the command, examine the output files, and if you are satisfied with the results, copy back the generated files to src/sql.

You might then want to build your plugin with 
> mvn clean install

to make it available to the build of your web site.

### Integrate the plugin

If you do not have a lutece web site suitable for testing, please create one as usual.

Then add these two dependencies (the liquibase plugin, and your own plugin) to the pom.xml of your web site :
```xml
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

The version number above for **plugin-liquibase** supposes that you have built it from source (branch LUT-26623) locally.

The version number for **plugin-testpourliquibase** is the one you used when creating a test plugin above.

The default behaviour of the liquibase plugin when starting a war is to do nothing. To enable it, you must define the following property in the properties file **liquibase-plugin.properties** (full relative path with **dev** profile : ./src/conf/dev/WEB-INF/conf/plugins/liquibase-plugin.properties) :
>liquibase.enabled.at.startup=true
Then build your site
>mvn clean fr.paris.lutece.tools:lutece-maven-plugin:4.1.3-SNAPSHOT:site-assembly -P dev

This command uses the profile "dev" and, importantly, specifies the version of the plugin, which places the SQL in the right folder so that liquibase can find them at run time in the classpath.

### Run

Deploy your site as usual in tomcat.

The liquibase plugin will output traces for its activity at startup between lines "LiquibaseRunner starting" and "LiquibaseRunner ended".
