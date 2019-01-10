package eu.chakhouski.juepak.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class PathUtils
{
    public static Path findCommonPath(boolean absolutize, Iterable<Path> paths)
    {
        final List<Path> list;
        if (paths instanceof List<?>)
        {
            list = (List<Path>)paths;
        }
        else if (paths instanceof Collection<?>)
        {
            list = new ArrayList<>((Collection<Path>) paths);
        }
        else
        {
            list = new ArrayList<>();

            for (Path t : paths)
                list.add(t);
        }

        return findCommonPath(absolutize, list.toArray(new Path[0]));
    }

    public static Path findCommonPath(boolean absolutize, Path... paths)
    {
        // Quit if no paths to compare
        if ((paths == null) || (paths.length == 0))
        {
            return null;
        }

        final int numPaths = paths.length;

        // Make absolute paths if necessary
        for (int i = 0; absolutize && i < numPaths; i++)
        {
            final Path p = paths[i];
            paths[i] = (p != null && !p.isAbsolute()) ? p.toAbsolutePath() : p;
        }

        // Find first non-null base path
        Path basePath = null;
        Path commonRoot = null;

        int indexOfBasePath;
        for (indexOfBasePath = 0; (basePath == null) && (indexOfBasePath < numPaths); indexOfBasePath++)
        {
            if (paths[indexOfBasePath] != null)
            {
                basePath = paths[indexOfBasePath];
                commonRoot = basePath.getRoot();
            }
        }

        // Quit if no base path or no (initial) common root
        if ((basePath == null) || (commonRoot == null))
        {
            return null;
        }

        // Set optimistic max matches
        int maxMatches = basePath.getNameCount();

        // Iterate trough path finding minimal similarity
        for (int i = indexOfBasePath + 1; (i < numPaths); i++)
        {
            final Path p = paths[i];
            if ((p != null))
            {
                if (commonRoot.equals(p.getRoot()))
                {
                    final int minLength = Math.min(basePath.getNameCount(), p.getNameCount());

                    // Common root is still the same, compare parts
                    for (int j = 0; j < minLength; j++)
                    {
                        if (!basePath.getName(j).equals(p.getName(j)))
                        {
                            // Found minimum, compare and quit loop
                            maxMatches = Math.min(maxMatches, j);
                            break;
                        }
                    }
                }
                else
                {
                    // No more common root
                    commonRoot = null;
                    break;
                }
            }
        }

        Path commonPath = commonRoot;

        // Connect paths together
        for (int i = 0; (commonPath != null) && (i < maxMatches); i++)
        {
            commonPath = commonPath.resolve(basePath.getName(i));
        }

        return commonPath;
    }

    /**
     * UE4 paths are '/'-separated, we need to create the same path artificially, until
     * {@link java.nio.file.Path#toString()} installs a platform-specific separator
     * (such as '/' for Mac or Linux or '\' for Windows)
     *
     * @param path Path to be converted to {@link java.lang.String}
     * @return A portable
     */
    public static String pathToPortableUE4String(Path path)
    {
        final StringJoiner sj = new StringJoiner("/");

        if (path != null)
        {
            final Path root = path.getRoot();

            if (root != null)
                sj.add(root.toString());

            for (int i = 0; i < path.getNameCount(); i++)
                sj.add(path.getName(i).toString());
        }

        return sj.toString();
    }
}
