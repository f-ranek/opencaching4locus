package org.bogus.domowygpx.activities;

import org.bogus.domowygpx.oauth.OKAPI;
import org.bogus.domowygpx.oauth.OKAPIFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CacheTypesConfigTest
{
    static OKAPI pl, de;
    
    void assertInConfig(CacheTypesConfig c, String item, boolean required)
    {
        String cfg = c.serializeToConfigString();
        boolean has = cfg.equals(item) || cfg.startsWith(item + "|") || 
                cfg.endsWith("|" + item) || cfg.contains("|" + item + "|");
        if (required && !has){
            Assert.fail("Expected item=" + item + " is missing in config=" + cfg);
        }
        if (!required && has){
            Assert.fail("Item=" + item + " is superfluous in config=" + cfg);
        }
    }
    
    @BeforeClass
    public static void setup()
    {
        pl = OKAPIFactory.getInstance("pl");
        de = OKAPIFactory.getInstance("de");
    }
    
    @Test
    public void testAll()
    {
        CacheTypesConfig c1 = new CacheTypesConfig(pl);
        Assert.assertFalse("At least one item is set", c1.isAnySet());
        c1.parseFromConfigString("ALL");
        
        for (String type : CacheTypesConfig.CACHE_TYPE_NAMES_PL){
            assertInConfig(c1, type, true);
        }
        for (String type : CacheTypesConfig.CACHE_TYPE_NAMES_DE){
            assertInConfig(c1, type, true);
        }
        
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
    }

    @Test
    public void testAllPl()
    {
        CacheTypesConfig c1 = new CacheTypesConfig(pl);
        c1.parseFromConfigString("All_pl");
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
        for (String type : CacheTypesConfig.CACHE_TYPE_NAMES_PL){
            assertInConfig(c1, type, true);
        }
        try{
            for (String type : CacheTypesConfig.CACHE_TYPE_NAMES_DE){
                assertInConfig(c1, type, true);
            }
            Assert.fail("Expected at least one missing cache type");
        }catch(java.lang.AssertionError ae){
            // ok
        }
    }
    
    @Test
    public void testPl1()
    {
        CacheTypesConfig c1 = new CacheTypesConfig(pl);
        c1.parseFromConfigString("All_pl");
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
        c1.set(2, false);
        Assert.assertFalse("All items are set", c1.isAllItemsSet());
        assertInConfig(c1, "Quiz", false);
        assertInConfig(c1, "All_pl", false);
        assertInConfig(c1, "All_de", false);
        assertInConfig(c1, "Math", false);
        c1.set(2, true);
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
        assertInConfig(c1, "Quiz", true);
        assertInConfig(c1, "All_pl", true);
        assertInConfig(c1, "All_de", false);
        assertInConfig(c1, "Math", false);
    }
    @Test
    public void testDe1()
    {
        CacheTypesConfig c1 = new CacheTypesConfig(de);
        c1.parseFromConfigString("All_de");
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
        c1.set(3, false);
        Assert.assertFalse("All items are set", c1.isAllItemsSet());
        assertInConfig(c1, "Quiz", false);
        assertInConfig(c1, "All_pl", false);
        assertInConfig(c1, "All_de", false);
        assertInConfig(c1, "Math", true);
        assertInConfig(c1, "Own", false);
        c1.set(3, true);
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
        assertInConfig(c1, "Quiz", true);
        assertInConfig(c1, "All_pl", false);
        assertInConfig(c1, "All_de", true);
        assertInConfig(c1, "Math", true);
        assertInConfig(c1, "Own", false);
    }
    
    @Test
    public void testMixed1()
    {
        CacheTypesConfig c1 = new CacheTypesConfig(pl);
        c1.parseFromConfigString("All_pl|All_de");
        c1.set(2, false);
        assertInConfig(c1, "Quiz", false);
        assertInConfig(c1, "All_pl", false);
        assertInConfig(c1, "All_de", false);
        assertInConfig(c1, "Math", true);
        c1.set(2, true);
        assertInConfig(c1, "Quiz", true);
        assertInConfig(c1, "All_pl", true);
        assertInConfig(c1, "All_de", true);
        assertInConfig(c1, "Math", true);
    }

    @Test
    public void testMixed2()
    {
        CacheTypesConfig c1 = new CacheTypesConfig(pl);
        c1.parseFromConfigString("All_pl|All_de");
        Assert.assertTrue("Not all items are set", c1.isAllItemsSet());
        c1.set(CacheTypesConfig.CACHE_TYPE_NAMES_PL.length-1, false);
        Assert.assertFalse("All items are set", c1.isAllItemsSet());
        assertInConfig(c1, "Own", false);
        assertInConfig(c1, "All_pl", false);
        assertInConfig(c1, "All_de", true);
        assertInConfig(c1, "Math", true);
        c1.set(CacheTypesConfig.CACHE_TYPE_NAMES_PL.length-1, true);
        assertInConfig(c1, "Own", true);
        assertInConfig(c1, "All_pl", true);
        assertInConfig(c1, "All_de", true);
        assertInConfig(c1, "Math", true);
    }
}
