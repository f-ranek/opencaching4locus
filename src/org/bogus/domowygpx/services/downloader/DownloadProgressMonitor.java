package org.bogus.domowygpx.services.downloader;

public interface DownloadProgressMonitor
{
    /**
     * Fired, then target file already exists or is scheduled to download in a different downloader
     * @param fileData
     */
    public void notifyFileSkipped(FileData fileData);
    /**
     * Fired before the file begins to download
     * @param fileData
     */
    public void notifyFileStarting(FileData fileData);
    /**
     * Fired, when the file begins to download, all headers are read, but content data is not
     * @param fileData
     * @param done The amount of data already downloaded (stored in a temp file from previous 
     *      session)
     * @param expectedFileSize Expected size of downloaded file, or -1 if unknown. The data to 
     *      download in this session can be calculated as <code>expectedFileSize - done</code>
     */
    public void notifyFileStarted(FileData fileData, long done, long expectedFileSize);
    /**
     * Fired periodically during file download. 
     * @param fileData
     * @param loopAmount Amount of data already downloaded since previous call
     * @param sessionDone Total amount of data downloaded in this session
     */
    public void notifyFileProgress(FileData fileData, int loopAmount, long sessionDone);
    /**
     * Fired when the file has finished, either successfully, or with an error
     * @param fileData
     * @param exception
     */
    public void notifyFileFinished(FileData fileData, Exception exception);
    /**
     * Fired when all tasks are done, and tasks queue is empty.
     * @param shutdownEvent Whether tasks are finished due to the call to 
     * {@link FilesDownloader#abortDownload()} or {@link FilesDownloader#stopDownload()} 
     */
    public void notifyTasksFinished(boolean shutdownEvent);
}
