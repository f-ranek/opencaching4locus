package org.bogus.domowygpx.application;


public class Application extends android.app.Application
{
    private OKAPI okApi;
    
    public OKAPI getOkApi()
    {
        return okApi;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        okApi = new OKAPI(this);
    }
    
    @Override
    public void onTerminate()
    {
        okApi = null;
        super.onTerminate();
    }
}
