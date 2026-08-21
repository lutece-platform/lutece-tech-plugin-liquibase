package fr.paris.lutece.plugins.liquibase.filters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.paris.lutece.plugins.liquibase.PluginMeta;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.utils.sql.SqlPathInfo;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.Resource;

/**
 * Comparator for use in the liquibase changelog config (resourceComparator attribute of includeAll).
 *
 * Orders SQL files as liquibase does by default (alphabetical path order), except for plugins declaring
 * an explicit ordering directive in the leading comment block of any of their scripts :
 *
 * <pre>
 * --liquibase formatted sql
 * --lutece runAfter:genericattributes
 * --changeset author:id
 * </pre>
 *
 * The directive is plugin-scoped : ALL the scripts of the declaring plugin are then ordered as if the
 * plugin's directory lived under the target plugin's directory, AFTER all the target's own scripts, while
 * physically staying in place. This replaces the fragile practice of placing a SQL file in another plugin's
 * directory to control execution order, which breaks version resolution (LUT-33232) : with runAfter, paths
 * still resolve to the true owner of each script.
 *
 * Directives chain to any depth : if A declares runAfter:B and B declares runAfter:C, the resulting order
 * is C, then B, then A (locations are resolved recursively along the dependency graph). Invalid directives
 * are ignored with an ERROR log and the plugin keeps its natural position : conflicting targets inside one
 * plugin, unknown or script-less target, self reference, involvement of core, and dependency cycles
 * (broken deterministically, plugins being resolved in name order).
 *
 * The directive must appear in the leading comment block, before the first non-comment line.
 *
 * Like the rest of this plugin, this class is deliberately not thread-safe : liquibase runs in the single
 * startup thread. Instantiated by liquibase itself (no-arg constructor), outside any DI.
 */
public class LuteceRunAfterComparator implements Comparator<String>
{
    private static final String WEB_INF_CLASSES = "WEB-INF/classes/";
    private static final Pattern RUN_AFTER_PATTERN = Pattern.compile("^--\\s*lutece\\b.*\\brunAfter:([\\p{Alnum}\\-]+)");
    private static final int MAX_HEADER_LINES = 20;
    private static final String CORE_PLUGIN_NAME = "core";
    // '~' sorts after any alphanumeric, so relocated scripts land after the target's own files.
    // FILE_MARKER sorts before AFTER_MARKER ("/" < "r"), so a relocated plugin's files always sort
    // before the files of plugins relocated after IT (chained directives).
    private static final String AFTER_MARKER = "/~runAfter/";
    private static final String FILE_MARKER = "/~/";

    /** normalized path -> sort key, for the files of relocated plugins only. Built on first use. */
    private Map<String, String> relocatedKeys;

    @Override
    public int compare(String left, String right)
    {
        if (relocatedKeys == null)
        {
            relocatedKeys = buildRelocatedKeys();
        }
        // Liquibase stores resources in a TreeSet based on this comparator : returning 0 deduplicates
        // the two forms of the same path (with and without WEB-INF/classes/), as the standard comparator
        // does. Distinct normalized paths always yield distinct keys since the key embeds the whole path.
        return keyOf(left).compareTo(keyOf(right));
    }

    private String keyOf(String path)
    {
        String normalized = normalize(path);
        return relocatedKeys.getOrDefault(normalized, normalized);
    }

    private static String normalize(String path)
    {
        return path.replace(WEB_INF_CLASSES, "");
    }

    private Map<String, String> buildRelocatedKeys()
    {
        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor())
        {
            return buildRelocatedKeys(accessor.search("sql", true));
        } catch (Exception e)
        {
            AppLogService.error("LiquibaseRunner. Could not scan SQL files for runAfter directives : falling back to alphabetical order", e);
            return new HashMap<>();
        }
    }

    private Map<String, String> buildRelocatedKeys(List<Resource> resources) throws IOException
    {
        // single pass over every SQL file : owning plugin, directory prefix, runAfter directive
        Map<String, String> basePrefixes = new HashMap<>();
        Map<String, List<String>> componentFiles = new HashMap<>();
        // TreeMap so that locations are resolved in name order : deterministic cycle breaking
        Map<String, String> targets = new TreeMap<>();
        Set<String> conflicting = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (Resource resource : resources)
        {
            String path = normalize(resource.getPath());
            if (!path.endsWith(".sql") || !seen.add(path))
                continue;
            SqlPathInfo info = SqlPathInfo.parse(path);
            // themes and unrecognized files keep their natural position
            if (info == null || info.isTheme())
                continue;
            String component = info.getFullPluginName();
            basePrefixes.putIfAbsent(component, directoryPrefix(path));
            componentFiles.computeIfAbsent(component, k -> new ArrayList<>()).add(path);
            String target = readRunAfterTarget(resource, path);
            if (target == null)
                continue;
            String previous = targets.put(component, target);
            if (previous != null && !previous.equals(target))
            {
                AppLogService.error("LiquibaseRunner. Plugin {} declares conflicting runAfter targets ({} and {}) : directives ignored", component, previous,
                        target);
                conflicting.add(component);
            }
        }
        targets.keySet().removeAll(conflicting);

        for (Iterator<Map.Entry<String, String>> iterator = targets.entrySet().iterator(); iterator.hasNext();)
        {
            Map.Entry<String, String> entry = iterator.next();
            String component = entry.getKey();
            String target = entry.getValue();
            String error = null;
            if (CORE_PLUGIN_NAME.equals(component) || CORE_PLUGIN_NAME.equals(target))
                error = "core cannot take part in runAfter ordering";
            else if (component.equals(target))
                error = "a plugin cannot run after itself";
            else if (PluginMeta.getPluginVersion(target) == null)
                error = "no such plugin is declared";
            else if (!basePrefixes.containsKey(target))
                error = "the target plugin has no SQL script";
            if (error != null)
            {
                AppLogService.error("LiquibaseRunner. runAfter:{} declared by plugin {} ignored : {}", target, component, error);
                iterator.remove();
            }
        }

        Map<String, String> locations = new HashMap<>();
        for (String component : new ArrayList<>(targets.keySet()))
        {
            resolveLocation(component, targets, basePrefixes, locations, new LinkedHashSet<>());
        }

        Map<String, String> keys = new HashMap<>();
        for (Map.Entry<String, String> entry : targets.entrySet())
        {
            String component = entry.getKey();
            String location = locations.get(component);
            AppLogService.info("LiquibaseRunner. Scripts of plugin {} will run after those of plugin {}", component, entry.getValue());
            for (String path : componentFiles.get(component))
            {
                keys.put(path, location + FILE_MARKER + path);
            }
        }
        return keys;
    }

    /**
     * Resolves the effective location of a plugin : its own directory, or, when it declares a (valid)
     * runAfter directive, a virtual directory sorting right after its target's effective location.
     * Cycles are broken at the plugin closing the loop, whose directive is discarded.
     */
    private String resolveLocation(String component, Map<String, String> targets, Map<String, String> basePrefixes, Map<String, String> locations,
            Set<String> visiting)
    {
        String location = locations.get(component);
        if (location != null)
            return location;
        String target = targets.get(component);
        if (target == null)
        {
            location = basePrefixes.get(component);
        }
        else if (!visiting.add(component))
        {
            AppLogService.error("LiquibaseRunner. Cycle detected in runAfter directives at plugin {} (chain : {}) : directive ignored", component, visiting);
            targets.remove(component);
            location = basePrefixes.get(component);
        }
        else
        {
            String parentLocation = resolveLocation(target, targets, basePrefixes, locations, visiting);
            visiting.remove(component);
            String existing = locations.get(component);
            // the recursion may have broken a cycle at this very component : its location is already settled
            if (existing != null)
                return existing;
            location = parentLocation + AFTER_MARKER + component;
        }
        locations.put(component, location);
        return location;
    }

    /**
     * Returns the plugin's SQL directory : the path minus the (core|plugin|upgrade)/file.sql trailing part.
     */
    private static String directoryPrefix(String path)
    {
        int last = path.lastIndexOf('/');
        int previous = last > 0 ? path.lastIndexOf('/', last - 1) : -1;
        return previous > 0 ? path.substring(0, previous) : path.substring(0, Math.max(last, 0));
    }

    private String readRunAfterTarget(Resource resource, String path)
    {
        try (InputStream stream = resource.openInputStream())
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count++ < MAX_HEADER_LINES)
            {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--"))
                {
                    // past the leading comment block : the directive must appear before any SQL
                    return null;
                }
                Matcher matcher = RUN_AFTER_PATTERN.matcher(trimmed);
                if (matcher.find())
                {
                    return matcher.group(1);
                }
            }
        } catch (IOException e)
        {
            AppLogService.error("LiquibaseRunner. Could not read header of " + path, e);
        }
        return null;
    }
}
