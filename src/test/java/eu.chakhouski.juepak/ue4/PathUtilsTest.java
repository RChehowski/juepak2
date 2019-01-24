package eu.chakhouski.juepak.ue4;

import eu.chakhouski.juepak.util.PathUtils;
import org.junit.Test;

import java.nio.file.Paths;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PathUtilsTest
{
    @Test
    public void findCommonPathTest()
    {
        // Same path(s) (should be equal to self)
        assertEquals(Paths.get("/a/b/c"), PathUtils.findCommonPath(false, singletonList(
                Paths.get("/a/b/c")
        )));

        assertEquals(Paths.get("/a/b/c"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c"),
                Paths.get("/a/b/c")
        )));

        // Difference
        assertEquals(Paths.get("/a/b"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c"),
                Paths.get("/a/b/d")
        )));

        assertEquals(Paths.get("/a/b"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c"),
                Paths.get("/a/b/c"),
                Paths.get("/a/b/d")
        )));

        // Length extent (extra items)
        assertEquals(Paths.get("/a/b/c"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c/d"),
                Paths.get("/a/b/c")
        )));

        assertEquals(Paths.get("/a/b/c"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c"),
                Paths.get("/a/b/c/d")
        )));

        // Nulls
        assertEquals(Paths.get("/a/b/c"), PathUtils.findCommonPath(false, asList(
                null,
                Paths.get("/a/b/c")
        )));

        assertEquals(Paths.get("/a/b/c"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c"),
                null
        )));

        assertEquals(Paths.get("/a/b"), PathUtils.findCommonPath(false, asList(
                Paths.get("/a/b/c"),
                null,
                Paths.get("/a/b/d")
        )));

        assertNull(PathUtils.findCommonPath(false, asList(null, null)));
    }
}
