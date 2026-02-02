package fr.paris.lutece.plugins.liquibase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.paris.lutece.utils.sql.SqlRegexpHelper;
import liquibase.resource.OpenOptions;
import liquibase.resource.Resource;
import liquibase.resource.ResourceAccessor;

/**
 * Filters SQL files before giving them to liquibase.
 *
 * This class is NOT thread-safe. This is not a problem as long as it is only used at lucene core startup on the main thread.
 *
 */
public class RegexpFilteringResourceAccessor implements ResourceAccessor
{
    private static final String CHANGESET_MARKER = "changeset ";
    private static final String FAIL_ON_ERROR_ATTR = "failOnError:";

    private final ResourceAccessor delegate;
    private final SqlRegexpHelper helper;
    private final boolean forceFailOnErrorFalse;
    // for better performance, re-used for all SQL files
    // this is what makes this class unsafe for use by several threads.
    private final ByteArrayOutputStream byteCache = new ByteArrayOutputStream();
    // for performance again :
    // liquibase reads the same file twice in a row
    // so we note the file name and re-use the byteCache if names match
    private String previousPath = null;

    public RegexpFilteringResourceAccessor(ResourceAccessor delegate, SqlRegexpHelper helper, boolean forceFailOnErrorFalse) throws IOException
    {
        this.delegate = delegate;
        this.helper = helper;
        this.forceFailOnErrorFalse = forceFailOnErrorFalse;
    }

    @Override
    public void close() throws Exception
    {
        delegate.close();
    }

    @Override
    public List<Resource> search(String path, boolean recursive) throws IOException
    {
        return filterResources(delegate.search(path, recursive));
    }

    @Override
    public List<Resource> getAll(String path) throws IOException
    {
        return filterResources(delegate.getAll(path));
    }

    private List<Resource> filterResources(List<Resource> source)
    {
        return (source == null || (helper == null && !forceFailOnErrorFalse)) ? source : source.stream().map(FilteringResource::new).collect(Collectors.toList());
    }

    @Override
    public List<String> describeLocations()
    {
        return delegate.describeLocations();
    }

    /**
     * Used by FilteringResource to filter a SQL file
     * @param in the original SQL file
     * @param path path of the original file, used to determine if we can reuse the last cached resource
     * @return a filtered InputStream
     * @throws IOException
     */
    private InputStream filterSQL(InputStream in, String path) throws IOException
    {
        if (previousPath == null || !previousPath.equals(path))
        {
            byteCache.reset();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    Stream<String> lines = reader.lines();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(byteCache, StandardCharsets.UTF_8));)
            {
                for (String line : (Iterable<String>) lines::iterator)
                {
                    String filtered = helper != null ? helper.filter(line) : line;
                    if (forceFailOnErrorFalse)
                    {
                        filtered = injectFailOnErrorFalse(filtered);
                    }
                    writer.append(filtered).append('\n');
                }
            }
            previousPath = path;
        }
        return new ByteArrayInputStream(byteCache.toByteArray());
    }

    /**
     * Injects failOnError:false into changeset declaration lines.
     * Handles both "-- changeset" and "--changeset" formats.
     */
    private String injectFailOnErrorFalse(String line)
    {
        String trimmed = line.trim();
        if (trimmed.startsWith("--") && trimmed.contains(CHANGESET_MARKER) && !trimmed.contains(FAIL_ON_ERROR_ATTR))
        {
            return line + " failOnError:false";
        }
        return line;
    }

    /**
     * Filters Resources with the configured regexes of SqlRegexpHelper
     */
    private class FilteringResource implements Resource
    {
        private final Resource actual;

        public FilteringResource(Resource actual)
        {
            this.actual = actual;
        }

        public String getPath()
        {
            return actual.getPath();
        }

        public InputStream openInputStream() throws IOException
        {
            return filterSQL(actual.openInputStream(), getPath());
        }

        public boolean isWritable()
        {
            return false;
        }

        public boolean exists()
        {
            return true;
        }

        public Resource resolve(String other)
        {
            return actual.resolve(other);
        }

        public Resource resolveSibling(String other)
        {
            return actual.resolveSibling(other);
        }

        public OutputStream openOutputStream(OpenOptions openOptions) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public OutputStream openOutputStream(boolean createIfNeeded) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public URI getUri()
        {
            return actual.getUri();
        }
    }
}
