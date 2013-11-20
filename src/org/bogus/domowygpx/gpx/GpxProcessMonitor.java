package org.bogus.domowygpx.gpx;

import java.io.File;

public interface GpxProcessMonitor
{
    public void onStartedCacheCode(String cacheCode/*, double latitude, double longitude*/);
    public void onPeriodicUpdate(String cacheCode);
    public void onEndedCacheCode(String cacheCode);
    public void onNewFile(int index, File fileName);
}
