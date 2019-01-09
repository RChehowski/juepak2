package eu.chakhouski.juepak.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
        Path commonPath = null;

        final int numPaths = paths.length;
        if (numPaths > 0)
        {
            // Absolutize paths if necessary
            for (int i = 0; absolutize && i < numPaths; i++)
            {
                final Path p = paths[i];
                paths[i] = (p != null && !p.isAbsolute()) ? p.toAbsolutePath() : p;
            }

            // Find first non-null base path
            Path basePath = null;
            Path basePathRoot = null;

            int indexOfBasePath;
            for (indexOfBasePath = 0; indexOfBasePath < numPaths && (basePath == null); indexOfBasePath++)
            {
                final Path maybeBasePath = paths[indexOfBasePath];
                if (maybeBasePath != null)
                {
                    basePath = maybeBasePath;
                    basePathRoot = basePath.getRoot();
                }
            }

            // Find matches
            int maxMatches = 0;
            for (int i = indexOfBasePath + 1; (basePath != null) && i < numPaths; i++)
            {
                final Path p = paths[i];
                if ((p != null) && Objects.equals(basePathRoot, p.getRoot()))
                {
                    commonPath = basePathRoot;

                    for (int j = 0; j < Math.min(basePath.getNameCount(), p.getNameCount()); j++)
                    {
                        if (!basePath.getName(j).equals(p.getName(j)))
                        {
                            maxMatches = Math.max(maxMatches, j);
                            break;
                        }
                    }
                }
            }

            // Connect paths together
            if (commonPath != null)
            {
                for (int i = 0; i < maxMatches; i++)
                    commonPath = commonPath.resolve(basePath.getName(i));
            }
        }

        return commonPath;
    }
}
