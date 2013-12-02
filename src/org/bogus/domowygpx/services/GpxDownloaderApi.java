package org.bogus.domowygpx.services;

import java.util.List;

import org.bogus.domowygpx.activities.TaskConfiguration;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;

import android.os.Messenger;

/**
 * Interface to interact with GpxDownloader service 
 * @author Boguś
 *
 */
public interface GpxDownloaderApi
{
    
    /**
     * Submit GPX download task, requires {@link TaskConfiguration} as {@link #INTENT_EXTRA_TASK_CONFIGURATION} extra parameter.
     */
    public static final String INTENT_ACTION_START_DOWNLOAD = "org.bogus.domowygpx.services.GpxDownloaderService.START_DOWNLOAD_GPX";

    public static final String INTENT_EXTRA_TASK_CONFIGURATION = "org.bogus.domowygpx.services.GpxDownloaderService.TASK_CONFIGURATION";
    /**
     * Optional {@link Messenger}, that will be notified in response to {@link #INTENT_ACTION_START_DOWNLOAD} with the taskId
     * of a new gpx task
     */
    public static final String INTENT_EXTRA_MESSENGER = "org.bogus.domowygpx.GpxDownloaderService.MESSENGER";
    
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
     * Returns string helping developer diagnose application state
     * @param taskId
     * @return
     */
    public String taskToDeveloperDebugString(int taskId);
    
    /**
     * Gets current GPX processing status (cache code, cache name, total kb, etc),
     * packs them into <ul>
     * <li>{@link GpxTaskEvent} with {@link GpxTaskEvent#eventType eventType} of <code>EVENT_TYPE_CACHE_CODE</code>,
     * <li>{@link GpxTask},
     * </ul>
     * and fires {@link GpxDownloaderListener#onTaskEvent(GpxTaskEvent, GpxTask)}  
     * @param taskId
     * @param listener
     * @return true, if task is processing
     */
    public boolean updateCurrentCacheStatus(int taskId, GpxDownloaderListener listener);
    
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
