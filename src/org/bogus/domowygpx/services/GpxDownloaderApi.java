package org.bogus.domowygpx.services;

import java.util.List;

import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;

/**
 * Interface to interact with GpxDownloader service 
 * @author Boguś
 *
 */
public interface GpxDownloaderApi
{
    /**
     * Sends an asynchronous signal to cancel the given task. When the task finishes, 
     * it will send an appropriate event
     * @param taskId
     * @return true, is the task has been signaled, false otherwise 
     * (i.e. task has already finished) 
     */
    boolean cancelTask(int taskId);
    
    /**
     * Lists task or tasks info
     * @param filterTaskId taskId, or -1 to get all tasks
     * @param attachEvents whether {@link GpxTask#events} should be populated
     * @return
     */
    List<GpxTask> getTasks(int filterTaskId, boolean attachEvents);
    
    /**
     * Removes task data and events. This call will only succeed for finished 
     * (either successfuly or erroneously) tasks. The call will invoke  
     * {@link GpxDownloaderListener#onTaskRemoved(int)} on all listeners.
     * 
     * @param taskId
     * @return true, if taks data has been removed, false otherwise
     */
    boolean removeTask(int taskId);
    
    /**
     * Registers client listener, events will be queued to the calling thread
     * {@link android.os.MessageQueue MessageQueue}, so make sure caller has one
     * @param listener Event listener
     */
    /* TODO: could also deliver in the worker thread, and make all 
     * synchronization effort on the client side
    */
    void registerEventListener(GpxDownloaderListener listener);

    /**
     * Unregisters client listener, so no more events will be delivered. 
     * @param listener
     * @return true, if the listener has been unregistered, false otherwise 
     */
    boolean unregisterEventListener(GpxDownloaderListener listener);
}
