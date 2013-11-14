package org.bogus.domowygpx.downloader;

public interface DownloadProgressMonitor
{
    public void notifyFileSkipped(FileData fileData);
    public void notifyFileStarted(FileData fileData);
    public void notifyFileProgress(FileData fileData, int doneKB, int totalKB);
    public void notifyFileFinished(DownloadedFileData fileData, Exception exception);
    public void notifyTasksFinished(boolean shutdownEvent);
}
