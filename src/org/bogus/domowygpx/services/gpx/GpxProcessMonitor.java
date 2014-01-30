package org.bogus.domowygpx.services.gpx;

import java.io.File;

public interface GpxProcessMonitor
{
    public void onStartedCacheCode(String cacheCode, String cacheName);
    public void onEndedCacheCode(String cacheCode);
    public void onNewFile(int index, File fileName);
}
