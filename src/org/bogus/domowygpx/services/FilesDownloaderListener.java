package org.bogus.domowygpx.services;

import org.bogus.domowygpx.services.FilesDownloaderService.FilesDownloadTask;
import org.bogus.domowygpx.services.downloader.FileData;

public interface FilesDownloaderListener 
{
    /**
     * Fired, then target file already exists
     * @param task
     * @param fileData
     */
    public void onFileSkipped(FilesDownloadTask task, FileData fileData);
    /**
     * Fired before the file begins to download
     * @param task
     * @param fileData
     */
    public void onFileStarting(FilesDownloadTask task, FileData fileData);
    /**
     * Fired, when the file begins to download, all headers are read, but content data is not
     * @param task
     * @param fileData
     */
    public void onFileStarted(FilesDownloadTask task, FileData fileData);
    /**
     * Fired periodically during file download
     * @param task
     * @param fileData
     */
    public void onFileProgress(FilesDownloadTask task, FileData fileData);

    /**
     * Fired when the file has finished, either successfully, or with an error
     * @param task
     * @param fileData
     * @param exception
     */
    public void onFileFinished(FilesDownloadTask task, FileData fileData, Exception exception);

    /**
     * Fired when all tasks are done, in any of the following situations <ul>
     * <li>All tasks are done
     * <li>Request to stop task has been fulfilled
     * <li>Request to cancel task has been fulfilled
     * </li>
     * @param task Tasks, that has finished 
     */
    public void onTaskFinished(FilesDownloadTask task);

    /**
     * Fired when task is removed from the database via call to
     * {@link FilesDownloaderApi#removeTask(int)}. Note, that if an old task is automatically
     * removed when the service starts, this callback is not fired 
     *  
     * @param task Tasks, that has finished 
     */
    public void onTaskRemoved(FilesDownloadTask task);
    
    /**
     * Fired after task state has changed to one of the: <ul>
     * <li>{@link FilesDownloadTask#STATE_RUNNING}, before first file notification is sent
     * <li>{@link FilesDownloadTask#STATE_PAUSING}, before {@link #onTaskPaused(FilesDownloadTask)} is fired 
     * <li>{@link FilesDownloadTask#STATE_CANCELLING}, before {@link #onTaskCancelled(FilesDownloadTask)} is fired
     * </ul>
     * @param task
     * @param previousState
     */
    public void onTaskStateChanged(FilesDownloadTask task, int previousState);
}
