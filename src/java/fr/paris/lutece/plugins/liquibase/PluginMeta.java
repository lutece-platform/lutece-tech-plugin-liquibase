package fr.paris.lutece.plugins.liquibase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import fr.paris.lutece.portal.service.init.LuteceInitException;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.util.filesystem.FileListFilter;

public class PluginMeta
{
    private static final String PATH_CONF = "path.conf";
    private static final String CORE_XML = "core.xml";
    private static final String PATH_PLUGIN = "path.plugins";
    private static final String EXTENSION_FILE = "xml";
    private static final Map<String, String> _mapPluginsMeta = new HashMap<>();

    /**
     * Pre-loads name and version from plugins to make them available (with getPluginVersion()) if needed before complete loading by init().
     * 
     * @throws LuteceInitException
     */
    public static void preloadMeta() throws LuteceInitException
    {
        File dirPlugin = new File(AppPathService.getPath(PATH_PLUGIN));

        if (dirPlugin.exists())// it should
        {
            // all plugins
            List<File> files = new ArrayList<>(Arrays.asList(dirPlugin.listFiles(new FileListFilter("", EXTENSION_FILE))));
            // and the core
            files.add(new File(AppPathService.getPath(PATH_CONF, CORE_XML)));
            try
            {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                for (File file : files)
                {
                    Document doc = builder.parse(file);
                    String name = doc.getElementsByTagName("name").item(0).getTextContent();
                    String version = doc.getElementsByTagName("version").item(0).getTextContent();
                    _mapPluginsMeta.put(name, version);
                }
            } catch (Exception e)
            {
                throw new LuteceInitException("preloadMeta failed", e);
            }
        }
    }

    /**
     * Returns a version for the given plugin name.
     * 
     * @param pluginName plugin name to look up
     * @return a version or null if not found
     */
    public static String getPluginVersion(String pluginName)
    {
        return _mapPluginsMeta.get(pluginName);
    }
}
