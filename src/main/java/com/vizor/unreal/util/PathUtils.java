package com.vizor.unreal.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public class PathUtils
{
    /**
     * Finds a common paths between a set of paths.
     * Same as {@see https://www.rosettacode.org/wiki/Find_common_directory_path}, but works with
     * native {@link java.nio.file.Path}, not just plain {@link java.lang.String} instances.
     *
     * @param compareAsAbsolute True to make paths absolute before comparing them.
     * @param paths An iterable something of paths.
     *
     * @return A common path (might be null if no common path found)
     */
    public static Path findCommonPath(final boolean compareAsAbsolute, final Iterable<Path> paths)
    {
        final List<Path> list;
        if (paths instanceof List<?>)
        {
            // Already a list, just cast
            list = (List<Path>)paths;
        }
        else if (paths instanceof Collection<?>)
        {
            // An unknown collection (set, deque, ...) perform a bulk add()
            list = new ArrayList<>((Collection<Path>) paths);
        }
        else if (paths != null)
        {
            // A plain iterable (but not null) add one-by-one
            list = new ArrayList<>();

            for (Path t : paths)
                list.add(t);
        }
        else
        {
            // Paths is null, set an empty list
            list = Collections.emptyList();
        }

        return findCommonPath(compareAsAbsolute, list.toArray(new Path[0]));
    }

    /**
     * Finds a common paths between a set of paths.
     * Same as {@see https://www.rosettacode.org/wiki/Find_common_directory_path}, but works with
     * native {@link java.nio.file.Path}, not just plain {@link java.lang.String} instances.
     *
     * @param compareAsAbsolute True to make paths absolute before comparing them.
     * @param paths An array of paths.
     *
     * @return A common path (might be null if no common path found)
     */
    public static Path findCommonPath(final boolean compareAsAbsolute, Path... paths)
    {
        // Quit if no paths to compare (paths might be null if user explicitly passed null here)
        if ((paths == null) || (paths.length == 0))
        {
            return null;
        }

        final int numPaths = paths.length;

        // Make absolute paths if user choose to compare as absolute
        for (int i = 0; compareAsAbsolute && i < numPaths; i++)
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

        // Quite expensive to call each time
        final int basePathNameCount = basePath.getNameCount();

        // Set optimistic max matches
        int maxMatches = basePathNameCount;

        // Iterate trough path finding minimal similarity
        for (int i = indexOfBasePath; (i < numPaths); i++)
        {
            final Path p = paths[i];
            if ((p != null))
            {
                final Path otherRoot = p.getRoot();

                if (commonRoot.equals(otherRoot))
                {
                    final int minLength = Math.min(basePathNameCount, p.getNameCount());

                    maxMatches = Math.min(maxMatches, minLength);

                    // Common root is still the same, compare parts
                    for (int j = 0; j < minLength; j++)
                    {
                        final Path baseNamePart = basePath.getName(j);
                        final Path otherNamePart = p.getName(j);

                        if (!baseNamePart.equals(otherNamePart))
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
        final String result;
        if (path != null)
        {
            // Always use '/' separator, not File.separator
            final StringJoiner sj = new StringJoiner("/");

            final Path root = path.getRoot();

            // Root may not exist for relative paths
            if (root != null)
            {
                // Remove backslashes (possible for windows)
                sj.add(root.toString().replaceAll("\\\\", ""));
            }

            // Quite expensive to call each time
            final int nameCount = path.getNameCount();

            // Sequentially add names
            for (int i = 0; i < nameCount; i++)
            {
                sj.add(String.valueOf(path.getName(i)));
            }

            result = sj.toString();
        }
        else
        {
            result = "";
        }

        return result;
    }
}
