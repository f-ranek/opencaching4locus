package org.bogus.domowygpx.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocationUtilsTest
{
    @Test
    public void testParseDegrees1()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("12.58788");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_DEGREES, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.58788d, x.second, 0.000001);
    }

    @Test
    public void testParseDegrees2()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("-12.58788");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_DEGREES, (int)x.first);
        Assert.assertEquals("Wrong coordinate", -12.58788d, x.second, 0.0000001);
    }

    @Test
    public void testParseMinutes1()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("12:15");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_MINUTES, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.25d, x.second, 0.0000001);
    }

    @Test
    public void testParseMinutes2()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("-12:15");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_MINUTES, (int)x.first);
        Assert.assertEquals("Wrong coordinate", -12.25d, x.second, 0.0000001);
    }

    @Test
    public void testParseMinutes3()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("12:15.25");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_MINUTES, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.2541666d, x.second, 0.0000001);
    }

    @Test
    public void testParseSeconds1()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("12:15:25");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_SECONDS, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.2569444d, x.second, 0.0000001);
    }

    @Test
    public void testParseSeconds2()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("12:15:25.81");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_SECONDS, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.2571694d, x.second, 0.0000001);
    }

    @Test
    public void testParseLat1a()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N 12°");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12d, x.second, 0.0000001);
    }
    @Test
    public void testParseLat1b()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N12°");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12d, x.second, 0.0000001);
    }
    @Test
    public void testParseLat1c()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N12°0'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12d, x.second, 0.0000001);
    }
    @Test
    public void testParseLat1d()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N12°0.0'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12d, x.second, 0.0000001);
    }
    @Test
    public void testParseLat1e()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N12° 0.0'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12d, x.second, 0.0000001);
    }

    @Test
    public void testParseLat1f()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N 12° 0.0'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12d, x.second, 0.0000001);
    }

    @Test
    public void testParseLat2a()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N 12°15'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.25d, x.second, 0.0000001);
    }

    @Test
    public void testParseLat2b()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("N 12°15.25'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.2541666d, x.second, 0.0000001);
    }

    @Test
    public void testParseLat2c()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("S 12°15.25'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", -12.2541666d, x.second, 0.0000001);
    }

    @Test
    public void testParseLon2a()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("E 12°15.25'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LON, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 12.2541666d, x.second, 0.0000001);
    }

    @Test
    public void testParseLon2b()
    {
        Pair<Integer, Double> x = LocationUtils.parseLocation("W 12°15.25'");
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LON, (int)x.first);
        Assert.assertEquals("Wrong coordinate", -12.2541666d, x.second, 0.0000001);
    }
    
    @Test
    public void testFormatDegrees1()
    {
        String s = LocationUtils.format(12d, LocationUtils.FORMAT_DEGREES);
        Assert.assertEquals("Wrong output", "12", s);
    }

    @Test
    public void testFormatDegrees2()
    {
        String s = LocationUtils.format(12.25d, LocationUtils.FORMAT_DEGREES);
        Assert.assertEquals("Wrong output", "12.25", s);
    }

    @Test
    public void testFormatMinutes1()
    {
        String s = LocationUtils.format(12.25d, LocationUtils.FORMAT_MINUTES);
        Assert.assertEquals("Wrong output", "12:15", s);
    }

    @Test
    public void testFormatMinutes2()
    {
        String s = LocationUtils.format(12d, LocationUtils.FORMAT_MINUTES);
        Assert.assertEquals("Wrong output", "12:00", s);
    }

    @Test
    public void testFormatMinutes3()
    {
        String s = LocationUtils.format(12.254166666d, LocationUtils.FORMAT_MINUTES);
        Assert.assertEquals("Wrong output", "12:15.25", s);
    }
    
    @Test
    public void testFormatMinutes4()
    {
        String s = LocationUtils.format(1.05d, LocationUtils.FORMAT_MINUTES);
        Assert.assertEquals("Wrong output", "1:03", s);
    }
    

    @Test
    public void testFormatSeconds1()
    {
        String s = LocationUtils.format(12.2569444d, LocationUtils.FORMAT_SECONDS);
        Assert.assertEquals("Wrong output", "12:15:25", s);
    }

    @Test
    public void testFormatSeconds2()
    {
        String s = LocationUtils.format(12.2571694d, LocationUtils.FORMAT_SECONDS);
        Assert.assertEquals("Wrong output", "12:15:25.81", s);
    }

    @Test
    public void testFormatLat1()
    {
        String s = LocationUtils.format(12.2571694d, LocationUtils.FORMAT_WGS84_LAT);
        Assert.assertEquals("Wrong output", "N 12° 15.43'", s);
    }
        
    
    @Test
    public void testParseLive1()
    {
        String s = "N 50° 16.825'";
        Pair<Integer, Double> x = LocationUtils.parseLocation(s);
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_WGS84_LAT, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 50.2804166d, x.second, 0.0000001);
    }

    @Test
    public void testParseLive2()
    {
        String s = "16:59.571";
        Pair<Integer, Double> x = LocationUtils.parseLocation(s);
        Assert.assertEquals("Wrong format", LocationUtils.FORMAT_MINUTES, (int)x.first);
        Assert.assertEquals("Wrong coordinate", 16.99285d, x.second, 0.0000001);
    }
}
