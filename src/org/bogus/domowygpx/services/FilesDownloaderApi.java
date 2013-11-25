package org.bogus.domowygpx.services;

import java.util.List;

import org.bogus.domowygpx.services.FilesDownloaderService.FilesDownloadTask;
import org.bogus.domowygpx.services.downloader.FileData;

import android.os.Messenger;

/**
 * Interface to interact with {@link FilesDownloaderService FilesDownloader
 * service}
 * 
 * @author Boguś
 * 
 */
public interface FilesDownloaderApi
{
    /**
     * Intent action to submit new files to download. The intent must have {@link #INTENT_EXTRA_FILES} attached, 
     * and optional {@link #INTENT_EXTRA_MESSENGER} 
     */
    public static final String INTENT_ACTION_SCHEDULE_FILES = "org.bogus.domowygpx.FilesDownloaderService.SCHEDULE_FILES";
    /**
     * An array of {@link FileData} objects to download
     */
    public static final String INTENT_EXTRA_FILES = "org.bogus.domowygpx.FilesDownloaderService.FILES";
    /**
     * Optional {@link Messenger}, that will be notified in response to {@link #INTENT_ACTION_SCHEDULE_FILES} with the taskId
     * of a new download task
     */
    public static final String INTENT_EXTRA_MESSENGER = "org.bogus.domowygpx.FilesDownloaderService.MESSENGER";
    
    /**
     * Schedules selected files to be downloaded
     * @param filesToDownload
     * @return taskId of a new task
     * @throws IllegalArgumentException if the files list is empty
     */
    int createTaskForFiles(List<FileData> filesToDownload)
    throws IllegalArgumentException;
    
    /**
     * Stops current activity, allowing files being currently downloaded to finish. This call is similar
     * to {@link #cancelTask(int)}, but do not abruptly stops current activity.
     * 
     * @param taskId task to pause
     * @return true, if tasks has been signaled to stop, false otherwise
     * 
     * @see #restartTask()
     */
    boolean stopTask(int taskId);

    /**
     * Restarts task, downloading scheduled files
     * @param taskId
     * @param restartFromScratch Tries to download all files, including that already marked as downloaded,
     *      as well as prviously failed
     * @return
     */
    boolean restartTask(int taskId, boolean restartFromScratch);
    
    /**
     * Cancels all downloads, both currently being processes, as well as
     * scheduled in the future. {@link FilesDownloaderListener#notifyFileFinished(org.bogus.domowygpx.downloader.DownloadedFileData, Exception)} 
     * is called for those tasks, that are being processes, but these queued are not signaled.
     * 
     * @param taskId task to cancel
     * @return true, if tasks has been signaled to cancel, false otherwise
     */
    boolean cancelTask(int taskId);

    /**
     * Removes task that is not running. Note, that old tasks are automatically removed when the 
     * service starts, and callback events are fired.
     * @param taskId
     * @return
     */
    boolean removeTask(int taskId);
    
    /**
     * Returns all tasks data. The tasks data is a current snapshot, which will not be updated
     * @return
     */
    List<FilesDownloadTask> getTasks();
    
    /**
     * Registers client listener, events will be queued to the calling thread
     * {@link android.os.MessageQueue MessageQueue}, so make sure caller has one
     * @param listener Event listener
     */
    void registerEventListener(FilesDownloaderListener listener);

    /**
     * Unregisters client listener, so no more events will be delivered. 
     * @param listener
     * @return true, if the listener has been unregistered, false otherwise 
     */
    boolean unregisterEventListener(FilesDownloaderListener listener);

}
