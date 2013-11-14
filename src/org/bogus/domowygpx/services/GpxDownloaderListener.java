package org.bogus.domowygpx.services;

import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;

public interface GpxDownloaderListener
{
    /**
     * Invoked when new task has been created, before any events 
     * for this task will be delivered 
     * @param taskId
     */
    void onTaskCreated(int taskId);
    
    /**
     * Invoked when new event arrives. The task parameter may be null, which
     * means that task data has not changed 
     * @param event
     * @param task
     */
    void onTaskEvent(final GpxTaskEvent event, final GpxTask task);

    /**
     * Invoked when task has been removed from the database
     * @param taskId
     */
    void onTaskRemoved(int taskId);
}
