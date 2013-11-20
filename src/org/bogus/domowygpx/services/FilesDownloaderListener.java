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
     * @param doneKB
     * @param totalKB Total file size, or -1 if unknown
     */
    public void onFileProgress(FilesDownloadTask task, FileData fileData, int doneKB, int totalKB);

    /**
     * Fired when the file has finished, either successfully, or with an error
     * @param task
     * @param fileData
     * @param exception
     */
    public void onFileFinished(FilesDownloadTask task, FileData fileData, Exception exception);

    /**
     * Fired when all tasks are done, and the tasks queue is empty
     *  
     * @param task Tasks, that has finished 
     */
    public void onTaskFinished(FilesDownloadTask task);

    /**
     * Fired when all tasks are done because of pause request via a call to
     * {@link FilesDownloaderApi#pauseTask(int)}
     *  
     * @param task Tasks, that has finished 
     */
    public void onTaskPaused(FilesDownloadTask task);
    
    /**
     * Fired when all tasks are done because of cancellation request via a call to
     * {@link FilesDownloaderApi#cancelTask(int)}
     *  
     * @param task Tasks, that has finished 
     */
    public void onTaskCancelled(FilesDownloadTask task);

    /**
     * Fired when task is removed from the database via call to
     * {@link FilesDownloaderApi#removeTask(int)}. Note, that if an old task is automatically
     * removed when the service starts, this callback is not fired 
     *  
     * @param task Tasks, that has finished 
     */
    public void onTaskRemoved(FilesDownloadTask task);
}
