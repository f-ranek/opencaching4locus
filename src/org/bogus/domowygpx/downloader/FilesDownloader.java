package org.bogus.domowygpx.downloader;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.bogus.domowygpx.apache.http.client.utils.DateUtils;

public class FilesDownloader implements Closeable
{
    final static AtomicInteger threadCount = new AtomicInteger();
    
    // private final static Log logger = LogFactory.getLog(FilesDownloader.class);
    private final ArrayList<DownloadedFileData> outputQueue = new ArrayList<DownloadedFileData>();
    private final List<DownloadProgressMonitor> observers = new CopyOnWriteArrayList<DownloadProgressMonitor>();
    private final Thread constructingThread;
    private volatile boolean abortFlag;
    private final ConcurrentHashMap<File, Boolean> filesOnHold;
    
    private final ThreadPoolExecutor executorService;
    private final List<DownloadTask> currentlyRunningTasks;
    private final HttpClient httpClient;
    
    class DownloadTask implements Runnable
    {
        final FileData data;
        public DownloadTask(FileData data)
        {
            this.data = data;
        }

        private volatile HttpUriRequest currentRequest;
        
        @Override
        public void run()
        {
            synchronized(currentlyRunningTasks){
                currentlyRunningTasks.add(this);
            }
            try{
                doDownloadInThread(data, this);
            }finally{
                synchronized(currentlyRunningTasks){
                    currentlyRunningTasks.remove(this);
                    checkIfAllTasksFinished();
                }
            }
        }

        protected void abort()
        {
            try{
                HttpUriRequest request = this.currentRequest;
                if (request != null){
                    request.abort();
                }
            }catch(Exception e){
                // ignore
            }
        }
    }
    
    public FilesDownloader(HttpClient httpClient, int numOfWorkerThreads)
    {
        this.constructingThread = Thread.currentThread();
        this.httpClient = httpClient;

        currentlyRunningTasks = new ArrayList<FilesDownloader.DownloadTask>(numOfWorkerThreads);
        final ThreadFactory tf = new ThreadFactory(){
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(constructingThread.getThreadGroup(), r,
                    "downloader-thread-" + threadCount.getAndIncrement(),
                    0);
                if (t.isDaemon()){
                    t.setDaemon(false);
                }
                if (t.getPriority() > Thread.MIN_PRIORITY+1){
                    t.setPriority(Thread.MIN_PRIORITY+1);
                }
                return t;
            }};

        PriorityBlockingQueue<Runnable> tasksQueue = new PriorityBlockingQueue<Runnable>(
            10, new Comparator<Runnable>(){
                @Override
                public int compare(Runnable r1, Runnable r2)
                {
                    final FileData o1 = ((DownloadTask)r1).data;
                    final FileData o2 = ((DownloadTask)r2).data;
                    int out = o1.priority - o2.priority;
                    if (out == 0){
                        String host1 = o1.source.getHost();
                        String host2 = o2.source.getHost();
                        if (host1 != null && host2 != null){
                            out = host1.compareTo(host2);
                        }
                    }
                    return out;
                }}); 
            
        executorService = new ThreadPoolExecutor(numOfWorkerThreads, numOfWorkerThreads,
            0L, TimeUnit.MILLISECONDS, tasksQueue, 
            tf);
        
        filesOnHold = new ConcurrentHashMap<File, Boolean>(numOfWorkerThreads);
    
    }
    
    public synchronized final boolean isClosed()
    {
        return executorService.isTerminating() || executorService.isShutdown() || executorService.isTerminated();
    }
    
    protected final void checkState()
    {
        if (isClosed()){
            throw new IllegalStateException("Downloader has been shut down");
        }
    }
    
    private void submitInternal(FileData fileData)
    {
        if (fileData == null){
            throw new NullPointerException("fileData");
        }
        DownloadTask task = new DownloadTask(fileData);
        this.executorService.submit(task);
    }
    
    public synchronized void submit(FileData fileData)
    {
        checkState();
        submitInternal(fileData);
    }

    public synchronized void submit(List<FileData> fileData)
    {
        checkState();
        synchronized(outputQueue){
            outputQueue.ensureCapacity(outputQueue.size() + fileData.size());
        }
        for (FileData fd : fileData){
            submitInternal(fd);
        }
    }

    protected synchronized void stopDownload(boolean abortFlag)
    {
        this.abortFlag |= abortFlag;
        executorService.getQueue().clear();
        if (this.abortFlag){
            executorService.shutdownNow();
        } else {
            executorService.shutdown();
        }
        /*if (!executorService.isTerminated()){
            checkIfAllTasksFinished();
        }*/
    }
    
    /**
     * Finish tasks in progress, and shutdown when all the files being currently downloaded are done.
     * Files in a queue are not started
     */
    public synchronized void stopDownload()
    {
        stopDownload(false);
    }
    
    /**
     * Abruptly aborts all tasks
     */
    public synchronized void abortDownload()
    {
        stopDownload(true);
    }

    protected void sendAbortSignalToWorkers()
    {
        synchronized(currentlyRunningTasks){
            for (DownloadTask wt : currentlyRunningTasks){
                wt.abort();
            }
        }
    }
        
    public void addObserver(DownloadProgressMonitor dpm)
    {
        observers.add(dpm);
    }

    public void removeObserver(DownloadProgressMonitor dpm)
    {
        observers.remove(dpm);
    }

    /**
     * Invokes {@link #abortDownload()}
     */
    @Override
    public void close()
    {
        abortDownload();
    }
    
    protected synchronized void checkIfAllTasksFinished()
    {
        synchronized(currentlyRunningTasks){
            if (currentlyRunningTasks.isEmpty() && executorService.getQueue().isEmpty())
            {
                final boolean hasBeenShutDown = isClosed();
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyTasksFinished(hasBeenShutDown);
                    }catch(Exception e2){
                        
                    }
                }
            }
        }
    }

    public synchronized boolean areAllTasksFinished()
    {
        synchronized(currentlyRunningTasks){
            return currentlyRunningTasks.isEmpty() && executorService.getQueue().isEmpty();
        }
    }
    
    public boolean awaitTermination(long timeout, TimeUnit unit)
    throws InterruptedException
    {
        return executorService.awaitTermination(timeout, unit);
    }
    
    protected void doDownloadInThread(final FileData data, final DownloadTask downloadTask)
    {
        HttpResponse response = null;
        OutputStream os = null;
        InputStream is = null;
        boolean isOk = false;

        {
            boolean skipped = false;
            if (filesOnHold.putIfAbsent(data.target, Boolean.TRUE) != null){
                skipped = true;
            }
            if (data.target.exists()){
                skipped = true;
            }
            if (skipped){
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyFileSkipped(data);
                    }catch(Exception e2){
                    }
                }
                return ;
            }
        }            
        final DownloadedFileData dfd = new DownloadedFileData();
        dfd.fileData = data;
        
        try{
            for (DownloadProgressMonitor o : observers){
                try{
                    o.notifyFileStarted(data);
                }catch(Exception e2){
                    
                }
            }
            
            final HttpGet get = new HttpGet(data.source);
            downloadTask.currentRequest = get;
            
            response = httpClient.execute(get);
            final StatusLine statusLine = response.getStatusLine();
            dfd.statusLine  = statusLine.toString();
            final Header[] allHeaders = response.getAllHeaders();
            dfd.headers = new String[allHeaders.length][];
            for (int i=0; i<allHeaders.length; i++){
                final Header h = allHeaders[i];
                final String[] h2 = new String[]{h.getName(), h.getValue()}; 
                dfd.headers[i] = h2;
            }
            
            if (statusLine.getStatusCode() == 404){
                throw new FileNotFoundException(data.source.toASCIIString());
            }
            if (statusLine.getStatusCode() == 204 || response.getEntity() == null){
                // no content
                return ;
            }
            
            final int fileSize;
            {
                long fileSize0 = response.getEntity().getContentLength();
                if (fileSize0 < 0 || fileSize0 > Integer.MAX_VALUE){
                    fileSize = -1;
                } else {
                    fileSize = (int)Math.ceil(fileSize0 / 1024.0);
                }
            }
            if (fileSize >= 1){
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyFileProgress(data, 0, fileSize);
                    }catch(Exception e2){
                    }
                }
            }
            
            
            byte[] buffer = new byte[16384];
            int lastSizeNotification = 0;
            is = response.getEntity().getContent();
            final File tempFile = new File(data.target.getParent(), data.target.getName() + ".progress");
            tempFile.getParentFile().mkdirs();
            os = new FileOutputStream(tempFile); 
            os = new BufferedOutputStream(os, buffer.length);
            int count;
            long totalCount = 0;
            while ((count = is.read(buffer)) != -1){
                os.write(buffer, 0, count);
                totalCount += count;
                int bytesDone = (int)Math.round(totalCount / 1024.0);
                if (bytesDone > lastSizeNotification){
                    lastSizeNotification = bytesDone;
                    for (DownloadProgressMonitor o : observers){
                        try{
                            o.notifyFileProgress(data, bytesDone, fileSize);
                        }catch(Exception e2){
                        }
                    }
                }
                if (abortFlag){
                    throw new InterruptedException();
                }
            }
            buffer = null;
            os.flush();
            os.close();
            os = null;
            is.close();
            is = null;
            dfd.size = totalCount;
            if (!tempFile.renameTo(data.target)){
                throw new IOException("Failed to rename temp file to " + data.target);
            }
            final Header lastModified = response.getFirstHeader("Last-Modified");
            if (lastModified != null){
                try{
                    final long date = DateUtils.parseDate(lastModified.getValue()).getTime();
                    if (date < System.currentTimeMillis() + 24L*60L*60L*1000L){
                        data.target.setLastModified(date);
                    }
                }catch(Exception e){
                    // ignore
                }
            }
            isOk = true;
        }catch(Exception e){
            dfd.exception = e;
            for (DownloadProgressMonitor o : observers){
                try{
                    o.notifyFileFinished(dfd, e);
                }catch(Exception e2){
                    
                }
            }
        }finally{
            filesOnHold.remove(data.target);
            synchronized(outputQueue){
                outputQueue.add(dfd);
            }
            downloadTask.currentRequest = null;
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
            //if (response instanceof CloseableHttpResponse){
            //    IOUtils.closeQuietly((CloseableHttpResponse)response);
            //}
            try{
                if (response != null){
                    HttpEntity e = response.getEntity();
                    if (e != null){
                        e.consumeContent();
                    }
                }
            }catch(Exception e){
                // ignore
            }
            if (isOk){
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyFileFinished(dfd, null);
                    }catch(Exception e2){
                        
                    }
                }
            }
            
        }
    }

    public int getNumOfWorkerThreads()
    {
        return executorService.getCorePoolSize();
    }

    public void setNumOfWorkerThreads(final int numOfWorkerThreads)
    {
        if (numOfWorkerThreads < 1){
            throw new IllegalArgumentException("numOfWorkerThreads=" + numOfWorkerThreads + " < 1");
        }
        final int curr = executorService.getCorePoolSize();
        if (curr == numOfWorkerThreads){
            return ;
        }
        if (numOfWorkerThreads > curr){
            executorService.setMaximumPoolSize(numOfWorkerThreads);
            executorService.setCorePoolSize(numOfWorkerThreads);
        } else {
            executorService.setCorePoolSize(numOfWorkerThreads);
            executorService.setMaximumPoolSize(numOfWorkerThreads);
        }
    }

}
