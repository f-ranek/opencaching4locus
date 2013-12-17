package org.bogus.domowygpx.application;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;

public class OKAPI
{
    private final Context ctx;
    private String key;
    private String secret;
    
    OKAPI(Context ctx)
    {
        this.ctx = ctx;
    }
    
    public String getAPIKey()
    {
        if (key == null){
            final String apiSecret = OKAPISecrets.getApiKey(ctx);
            int idx = apiSecret.indexOf('|');
            if (idx > 0){
                key = apiSecret.substring(0, idx);
                secret = apiSecret.substring(idx+1);
            } else {
                key = apiSecret;
            }
        }
        return key;
    }
    
    public String getAPIUrl()
    {
        return "http://opencaching.pl/okapi/";
    }
    
    public String getOAuthAuthorizeUrl()
    {
        return getAPIUrl() + "services/oauth/request_token";   
    }
   
    public String getOAuthRequestTokenUrl()
    {
        return getAPIUrl() + "services/oauth/authorize";
    }
    
    public String getOAuthAccessTokenUrl()
    {
        return getAPIUrl() + "services/oauth/access_token";
    }

    /*public String maskObject(Object obj)
    {
        if (obj == null){
            return null;
        }
        return obj.toString().replace(getAPIKey(), "xxxxxxxxxx");
    }*/
    
    public static OKAPI getInstance(Application application)
    {
        return ((org.bogus.domowygpx.application.Application)application).getOkApi();
    }

    public static OKAPI getInstance(Service service)
    {
        return getInstance(service.getApplication());
    }

    public static OKAPI getInstance(Activity activity)
    {
        return getInstance(activity.getApplication());
    }

    public static OKAPI getInstance(Context context)
    {
        if (context instanceof Service){
           return getInstance((Service)context);
        } else
        if (context instanceof Activity){
            return getInstance((Activity)context);
        } else
        if (context instanceof Application){
            return getInstance((Application)context);
        } else {
            throw new IllegalStateException("Unknow context class=" + context.getClass().getName());
        }
    }
}
