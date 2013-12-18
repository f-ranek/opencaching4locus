package org.bogus.domowygpx.oauth;

import org.bogus.domowygpx.application.Application;

import android.app.Activity;
import android.app.Service;
import android.content.Context;

public class OKAPI
{
    private volatile static boolean created;
    
    final Context ctx;
    final OAuth oauth;
    
    public OKAPI(Application ctx)
    {
        if (created){
            throw new IllegalStateException();
        }
        created = true;
        this.ctx = ctx;
        this.oauth = new OAuth(ctx, this);
    }
    
    public String getAPIUrl()
    {
        return "http://opencaching.pl/okapi/";
    }
    
    /*public String maskObject(Object obj)
    {
        if (obj == null){
            return null;
        }
        return obj.toString().replace(getAPIKey(), "xxxxxxxxxx");
    }*/
    
    public static OKAPI getInstance(android.app.Application application)
    {
        return ((Application)application).getOkApi();
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

    public OAuth getOAuth()
    {
        return oauth;
    }
}
