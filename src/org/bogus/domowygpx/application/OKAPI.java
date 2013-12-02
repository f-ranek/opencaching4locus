package org.bogus.domowygpx.application;

import android.app.Application;
import android.app.Service;
import android.content.Context;

public class OKAPI
{
    private final Context ctx;
    private String key;
    
    OKAPI(Context ctx)
    {
        this.ctx = ctx;
    }
    
    public String getAPIKey()
    {
        if (key == null){
            key = OKAPISecrets.getApiKey(ctx);            
        }
        return key;
    }
    
    public String getAPIUrl()
    {
        return "http://opencaching.pl/okapi/";
    }
    
    public String maskObject(Object obj)
    {
        if (obj == null){
            return null;
        }
        return obj.toString().replace(getAPIKey(), "xxxxxxxxxx");
    }
    
    public static OKAPI getInstance(Application application)
    {
        return ((org.bogus.domowygpx.application.Application)application).getOkApi();
    }

    public static OKAPI getInstance(Service service)
    {
        return getInstance(service.getApplication());
    }
}
