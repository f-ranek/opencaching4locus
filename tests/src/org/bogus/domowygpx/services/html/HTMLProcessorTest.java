package org.bogus.domowygpx.services.html;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HTMLProcessorTest
{
    
    @Test
    public void testEscape1()
    {
        HTMLProcessor hp = new HTMLProcessor(null);
        String escaped = hp.escapeHtml("http://bogus.ovh.org/oc4l/");
        Assert.assertEquals("Wrong escape", "http://bogus.ovh.org/oc4l/", escaped);
    }

    @Test
    public void testEscape2()
    {
        HTMLProcessor hp = new HTMLProcessor(null);
        String escaped = hp.escapeHtml("http://bogus.ovh.org/oc4l/?param=test&param2=du<pa");
        Assert.assertEquals("Wrong escape", "http://bogus.ovh.org/oc4l/?param=test&amp;param2=du&lt;pa", escaped);
    }

    @Test
    public void testUnescape1()
    {
        HTMLProcessor hp = new HTMLProcessor(null);
        String ubescaped = hp.escapeHtml("http://bogus.ovh.org/oc4l/");
        Assert.assertEquals("Wrong unescape", "http://bogus.ovh.org/oc4l/", ubescaped);
    }

    @Test
    public void testUnescape2()
    {
        HTMLProcessor hp = new HTMLProcessor(null);
        String unescaped = hp.unescapeHtml("http://bogus.ovh.org/oc4l/?param=test&amp;param2=du&lt;pa");
        Assert.assertEquals("Wrong unescape", "http://bogus.ovh.org/oc4l/?param=test&param2=du<pa", unescaped);
    }

    @Test
    public void testUnescape3()
    {
        HTMLProcessor hp = new HTMLProcessor(null);
        String unescaped = hp.unescapeHtml("http://bogus.ovh.org/oc4l/?param=test&amp;param2=du&lt;pa&amp;x=&#x40;");
        Assert.assertEquals("Wrong unescape", "http://bogus.ovh.org/oc4l/?param=test&param2=du<pa&x=@", unescaped);
    }

    @Test
    public void testUnescape4()
    {
        HTMLProcessor hp = new HTMLProcessor(null);
        String unescaped = hp.unescapeHtml("http://bogus.ovh.org/oc4l/?param=test&amp;param2=du&lt;pa&amp;x=&#64;");
        Assert.assertEquals("Wrong unescape", "http://bogus.ovh.org/oc4l/?param=test&param2=du<pa&x=@", unescaped);
    }
}

