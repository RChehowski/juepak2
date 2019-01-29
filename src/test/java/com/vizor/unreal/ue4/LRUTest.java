package com.vizor.unreal.ue4;

import com.vizor.unreal.util.LRU;
import org.junit.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class LRUTest
{
    @Test
    public void getTest()
    {
        final LRU<Integer, String> lru = new LRU<>(1);

        lru.put(0, "Zero");
        assertEquals("Zero", lru.get(0));
    }

    @Test
    public void containsTest()
    {
        final LRU<Integer, String> lru = new LRU<>(1);

        lru.put(0, "Zero");
        assertTrue(lru.containsKey(0));
    }

    @Test
    public void simplePreemptionTest()
    {
        final LRU<Integer, String> lru = new LRU<>(3);

        lru.put(0, "Zero");
        lru.put(1, "One");
        lru.put(2, "Two");

        // Should replace (0 -> Zero)
        lru.put(3, "Three");

        assertTrue(lru.containsKey(1));
        assertTrue(lru.containsKey(2));
        assertTrue(lru.containsKey(3));

        // 0 should be replaced with 3
        assertFalse(lru.containsKey(0));
    }

    @Test
    public void advPreemptionTest()
    {
        final LRU<Integer, String> lru = new LRU<>(3);

        lru.put(0, "Zero");
        lru.put(1, "One");
        lru.put(2, "Two");

        assertEquals("Zero", lru.get(0));

        // Should replace (1 -> Zero)
        lru.put(3, "Three");

        assertTrue(lru.containsKey(0));
        assertTrue(lru.containsKey(2));
        assertTrue(lru.containsKey(3));

        // 1 should be replaced with 3
        assertFalse(lru.containsKey(1));
    }
}
