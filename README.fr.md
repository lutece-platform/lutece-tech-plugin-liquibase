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

Par défaut, le plugin ne fait rien. Toutes les configurations sont définies dans le fichier `liquibase-plugin.properties` .

## Propriétés générales

| Propriété| Description| Valeur par défaut|
|-----------------|-----------------|-----------------|
|  `liquibase.enabled.at.startup` | Activer l'exécution du plugin au démarrage de l'application| false|
|  `liquibase.safeRun` | Forcer l'exécution de liquibase sans vérifier si les fichiers existants sont gérés par liquibase (false pour contourner la vérification de sécurité)| true|
|  `liquibase.first.run.request` | Requête SQL pour vérifier s'il s'agit de la première exécution (vérifie la table DATABASECHANGELOG)| select count(*) FROM information_schema.tables where table_name='DATABASECHANGELOG';|
|  `liquibase.empty.db.request` | Requête SQL pour vérifier si la base de données est vide| SELECT count(*) FROM information_schema.tables where table_schema=database();|

## Contrôle de version

| Propriété| Description| Valeur par défaut|
|-----------------|-----------------|-----------------|
|  `liquibase.accept.unstable.versions` | Autoriser l'exécution de liquibase sur les fichiers de mise à jour des versions instables (alpha, beta, rc) à chaque démarrage| false|
|  `liquibase.accept.snapshot.versions` | Autoriser l'exécution de liquibase sur les fichiers de mise à jour des versions snapshot à chaque démarrage| false|

## Mode de simulation (Dry Run)

| Propriété| Description| Valeur par défaut|
|-----------------|-----------------|-----------------|
|  `liquibase.dryrun` | Activer le mode de simulation pour générer un script SQL au lieu d'appliquer les modifications à la base de données| false|
|  `liquibase.dryrun.output.file` | Chemin du fichier de sortie pour le script SQL en mode simulation| WEB-INF/plugins/liquibase/liquibase-dryrun.sql|

## Analytique et journalisation

| Propriété| Description| Valeur par défaut|
|-----------------|-----------------|-----------------|
|  `liquibase.analytics.enabled` | Activer l'envoi d'analytiques à Liquibase| false|
|  `liquibase.sql.logLevel` | Définir le niveau de journalisation pour la sortie SQL (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL, DEBUG)| DEBUG|

## Mode de migration

| Propriété| Description| Valeur par défaut|
|-----------------|-----------------|-----------------|
|  `liquibase.migration.mode` | Activer le mode de migration pour mettre à jour les versions du plugin dans le datastore sans appliquer les changesets de liquibase| false|

## Gestion des erreurs

| Propriété| Description| Valeur par défaut|
|-----------------|-----------------|-----------------|
|  `liquibase.failOnError` | Arrêter sur une erreur SQL dans un changeset (true) ou continuer avec les changesets suivants (false)| true|


# Ordre d'exécution : la directive d'en-tête runAfter

Par défaut, liquibase exécute les fichiers SQL dans l'ordre alphabétique des chemins. Quand un plugin a besoin que ses scripts s'exécutent après ceux d'un autre plugin (typiquement pour insérer des lignes dans des tables créées par cet autre plugin), le contournement historique consistait à livrer le fichier SQL directement dans le répertoire de l'autre plugin. Cette pratique casse la résolution de version : l'inclusion du fichier est alors décidée par rapport à la version de l'autre plugin, jamais celle du propriétaire.

La directive d'en-tête `runAfter` remplace cette pratique. Elle se déclare dans le bloc de commentaires de tête (avant le premier changeset) de n'importe quel script du plugin :

```sql
--liquibase formatted sql
--lutece runAfter:genericattributes
--changeset forms:init_db_generic_attributes_forms.sql
INSERT INTO genatt_entry_type (id_type, title, ...) VALUES (1, 'Bouton radio', ...);
```

Tous les scripts du plugin déclarant (ici `forms`) sont alors ordonnés après tous les scripts du plugin cible (ici `genericattributes`), tout en restant physiquement dans le répertoire de leur propriétaire — la résolution de version reste donc correcte.

* La directive a une portée plugin : une seule déclaration dans un seul fichier suffit pour tout le plugin.
* Les directives se chaînent à n'importe quelle profondeur : si A déclare runAfter:B et B déclare runAfter:C, l'ordre résultant est C, puis B, puis A.
* Les directives invalides (cibles en conflit dans un même plugin, cible inconnue ou sans script, auto-référence, implication de core, cycles de dépendances) sont ignorées avec un log ERROR et le plugin garde sa position naturelle.


# Scripts pré-exécutés : le fichier réservé prerun_db

Certaines remises en état ponctuelles doivent être visibles de la résolution de versions du run liquibase principal, sans quoi elles ne servent à rien. Cas typique, un plugin renommé entre deux releases : son répertoire SQL change, donc sur un site existant le nouveau composant n'a pas de version au datastore — il est traité comme une installation neuve, ses scripts d'upgrade sont écartés, puis la version enregistrée saute à la release courante : les upgrades écartés ne seront plus jamais joués. Un script de migration n'y peut rien tant qu'il s'exécute dans l'update principal : les décisions d'inclusion sont déjà prises quand il tourne.

Un plugin peut livrer **un fichier SQL liquibase réservé par composant**, à un chemin fixe dérivé du nom du composant :

```
sql/plugins/<plugin>/plugin/prerun_db_<plugin>.sql
sql/plugins/<plugin>/modules/<module>/plugin/prerun_db_<plugin>-<module>.sql
```

Tous les fichiers `prerun_db_*` sont exécutés dans un update liquibase préliminaire, avant la construction et le filtrage du changelog principal, et sont exclus de ce dernier. Un fichier « liquibase formatted sql » pouvant contenir plusieurs changesets, ce fichier est le mini-changelog de pré-exécution du composant : chaque release qui a besoin d'une remise en état ajoute un changeset au même fichier. Les changesets sont enregistrés dans `DATABASECHANGELOG`, exécutés une seule fois, avec preconditions et `validCheckSum`.

Exemple — après le renommage d'un plugin `oldname` en `newname`, `sql/plugins/newname/plugin/prerun_db_newname.sql` :

```sql
-- liquibase formatted sql
-- changeset newname:prerun-rename-oldname
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:1 SELECT COUNT(DISTINCT 1) FROM DATABASECHANGELOG WHERE FILENAME LIKE 'sql/plugins/oldname/%'
UPDATE core_datastore SET entity_key = REPLACE(entity_key,'core.plugins.status.oldname.','core.plugins.status.newname.') WHERE entity_key LIKE '%core.plugins.status.oldname.%';
UPDATE DATABASECHANGELOG SET FILENAME = REPLACE(FILENAME,'sql/plugins/oldname/','sql/plugins/newname/') WHERE FILENAME LIKE 'sql/plugins/oldname/%';
```

La version installée survit alors au renommage : le run principal la voit et inclut les bons scripts d'upgrade, et les changesets déjà exécutés gardent leur identité sous les nouveaux chemins.

Règles d'écriture :

* Protéger chaque changeset par des preconditions retombant en `MARK_RAN` à la fois `onFail` et `onError` : le fichier s'exécute sur tout état de base, y compris une base vide à la première installation, où la requête de precondition elle-même peut échouer.
* Ne jamais modifier le corps d'un changeset exécuté sans déclarer `validCheckSum:`.
* Une remise en état déjà exécutée sous un autre chemin sur des sites existants doit, en rejoignant le fichier réservé, déclarer son identité d'origine avec `logicalFilePath:` sur sa ligne changeset.
* L'ordre d'exécution entre fichiers `prerun_db_*` est alphabétique ; `runAfter` ne s'applique pas au run préliminaire, qui est aussi sauté en mode migration.
* Un fichier `prerun_db_*` à un emplacement inattendu, mal nommé, ou d'un composant non déclaré est un défaut de packaging, signalé comme un composant non résolu (démarrage refusé avec `liquibase.safeRun=true`).


[Maven documentation and reports](https://dev.lutece.paris.fr/plugins/plugin-liquibase/)



 *generated by [xdoc2md](https://github.com/lutece-platform/tools-maven-xdoc2md-plugin) - do not edit directly.*