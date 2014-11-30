package org.bogus.domowygpx.oauth;

import android.content.Context;

public class OKAPIFactory
{
    public static Context ctx = new Context();
    
    public static OKAPI getInstance(String installation)
    {
        ctx.preferences.put("okapi.installation", installation);
        OKAPI okapi = new OKAPI(ctx);
        return okapi;
    }
}
