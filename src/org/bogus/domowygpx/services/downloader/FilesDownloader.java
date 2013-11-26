package org.bogus.domowygpx.services.downloader;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.bogus.domowygpx.apache.http.client.utils.DateUtils;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.domowygpx.utils.HttpException;
import org.bogus.logging.LogFactory;

public class FilesDownloader implements Closeable
{
    final static AtomicInteger threadCount = new AtomicInteger();
    
    final static Set<String> ignoredHeaders = new HashSet<String>(Arrays.asList(
        "keep-alive", "connection", "proxy-connection"));
    
    private final static Log logger = LogFactory.getLog(FilesDownloader.class);
    private final List<DownloadProgressMonitor> observers = new CopyOnWriteArrayList<DownloadProgressMonitor>();
    final Thread constructingThread;
    private volatile boolean abortFlag;
    private final ConcurrentMap<File, Boolean> filesOnHold;
    
    private final ThreadPoolExecutor executorService;
    final List<DownloadTask> currentlyRunningTasks;
    private final HttpClient httpClient;
    
    boolean paused; 
    
    class DownloadTask implements Runnable
    {
        final FileData data;
        public DownloadTask(FileData data)
        {
            this.data = data;
        }

        volatile HttpUriRequest currentRequest;
        
        @Override
        public void run()
        {
            synchronized(currentlyRunningTasks){
                currentlyRunningTasks.add(this);
            }
            try{
                synchronized(FilesDownloader.this){
                    if (paused){ // find some class that implements this semaphore pattern
                        do{
                            try{
                                FilesDownloader.this.wait();
                            }catch(InterruptedException ie){
                                // ignore
                            }
                            if (isClosed()){
                                return ;
                            }
                        }while(paused);
                    }
                }
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
    
    public FilesDownloader(HttpClient httpClient, int numOfWorkerThreads, 
        ConcurrentMap<File, Boolean> filesOnHold)
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
                t.setPriority(Thread.NORM_PRIORITY-1);
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
            0L, TimeUnit.MILLISECONDS, tasksQueue, tf);
        
        if (filesOnHold != null){
            this.filesOnHold = filesOnHold; 
        } else {
            this.filesOnHold = new ConcurrentHashMap<File, Boolean>(numOfWorkerThreads);
        }
    
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
        this.executorService.execute(task);
    }
    
    public synchronized void submit(FileData fileData)
    {
        checkState();
        submitInternal(fileData);
    }

    public synchronized void submit(List<FileData> fileData)
    {
        checkState();
        for (FileData fd : fileData){
            submitInternal(fd);
        }
    }

    protected synchronized void stopDownload(boolean abortFlag)
    {
        this.abortFlag |= abortFlag;
        executorService.getQueue().clear();
        setPaused(false);
        if (this.abortFlag){
            synchronized(currentlyRunningTasks){
                for (DownloadTask wt : currentlyRunningTasks){
                    wt.abort();
                }
            }
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
    public void stopDownload()
    {
        stopDownload(false);
    }
    
    /**
     * Abruptly aborts all tasks
     */
    public void abortDownload()
    {
        stopDownload(true);
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
    
    protected void addCommonHeaders(HttpRequest request)
    {
        request.addHeader("Accept", "*/*");      
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
        
        try{
            data.sessionAmount = 0;
            data.initialSize = 0;
            data.expectedSize = -1;
            
            for (DownloadProgressMonitor o : observers){
                try{
                    o.notifyFileStarting(data);
                }catch(Exception e2){
                    
                }
            }

            boolean acceptByteRanges = false;
            boolean tryReasume = false;
            boolean gotFullFile = false;
            boolean gotPartialFile = false;
            long currentFileSize = 0;
            final File tempFile = new File(data.target.getParent(), data.target.getName() + ".progress");
            String eTag = null;
            String lastModified = null;
            
            // TODO: implement config flag to disable this functionality :)
            if (tempFile.exists() && tempFile.canRead() && tempFile.canWrite()){
                // try parse previous headers
                if (data.headers != null){
                    for (String[] header: data.headers){
                        String headerName = header[0].toLowerCase(Locale.US);
                        if (headerName.equals("accept-ranges")){
                            if (header[1].toLowerCase(Locale.US).equals("bytes")){
                                acceptByteRanges = true;
                            }
                        } else 
                        if (headerName.equals("last-modified")){
                            lastModified = header[1];
                        } else 
                        if (headerName.equals("etag")){
                            eTag = header[1];
                        }
                    }
                }
            }
            HttpGet get = null; 
            if (acceptByteRanges){
                // implement If-Range http header
                // or maybe not... http://forums.iis.net/t/1201245.aspx 
                // (Managed handler crashes on If-range request)
                get = new HttpGet(data.source);
                if (eTag != null || lastModified != null){
                    if (eTag != null){
                        get.addHeader("If-None-Match", eTag);
                    }
                    if (lastModified != null){
                        get.addHeader("If-Modified-Since", lastModified);
                    }
                }
                addCommonHeaders(get);
                downloadTask.currentRequest = get;
                response = httpClient.execute(get);
                final int httpCode = response.getStatusLine().getStatusCode(); 
                if (httpCode == 304){
                    // not modified: try reasume
                    tryReasume = true;
                    ResponseUtils.closeResponse(response);
                } else
                if (httpCode == 200){
                    // modified: we got full file
                    gotFullFile = true;
                } else 
                if (httpCode == 404){
                    throw new FileNotFoundException(data.source.toASCIIString());
                } else 
                if (httpCode == 204 || response.getEntity() == null){
                    // no content
                    throw new HttpException(httpCode, "No content");
                } else {
                    logger.warn("Got unexpected response while checking if file has been modified: " + response.getStatusLine());
                    ResponseUtils.closeResponse(response);
                }
            }
            
            if (tryReasume){
                get = new HttpGet(data.source);
                currentFileSize = tempFile.length();
                get.addHeader("Range", "bytes=" + currentFileSize + "-");
                addCommonHeaders(get);
                downloadTask.currentRequest = get;
                response = httpClient.execute(get);
                final int httpCode = response.getStatusLine().getStatusCode(); 
                if (httpCode == 200){
                    // This should not happen, but we got full file
                    gotFullFile = true;
                    currentFileSize = 0;
                } else 
                if (httpCode == 206){
                    gotPartialFile = true;
                } else
                if (httpCode == 416){ 
                    // Requested range not satisfiable
                    // -> download full file
                    currentFileSize = 0;
                } else 
                if (httpCode == 204 || response.getEntity() == null){
                    // no content
                    throw new HttpException(httpCode, "No content");
                } else {
                    currentFileSize = 0;
                    logger.warn("Got unexpected response while asking for partial file data: " + response.getStatusLine());
                    ResponseUtils.closeResponse(response);
                }
            }
            
            if (!gotFullFile && !gotPartialFile){
                get = new HttpGet(data.source);
                addCommonHeaders(get);
                downloadTask.currentRequest = get;
                response = httpClient.execute(get);
            }
            
            final StatusLine statusLine = response.getStatusLine();
            data.statusLine  = statusLine.toString();
            {
                final Header[] allHeaders = response.getAllHeaders();
                final List<String[]> allHeaders2 = new ArrayList<String[]>(allHeaders.length);
                
                for (int i=0; i<allHeaders.length; i++){
                    final Header h = allHeaders[i];
                    if (!ignoredHeaders.contains(h.getName().toLowerCase(Locale.US))){
                        final String[] h2 = new String[]{h.getName(), h.getValue()}; 
                        allHeaders2.add(h2);
                    }
                }
                data.headers = allHeaders2.toArray(new String[allHeaders2.size()][]);
            }
            
            
            if (statusLine.getStatusCode() == 404){
                throw new FileNotFoundException(data.source.toASCIIString());
            }
            if (statusLine.getStatusCode() == 204 || response.getEntity() == null){
                // no content
                throw new HttpException(statusLine.getStatusCode(), "No content");
            }
            
            if (!gotFullFile && !gotPartialFile && statusLine.getStatusCode() != 200){
                throw HttpException.fromHttpResponse(response, get);
            }
            
            {
                long expectedFileSize = response.getEntity().getContentLength();
                if (expectedFileSize < 0){
                    expectedFileSize = -1;
                }
                data.initialSize = currentFileSize;
                data.expectedSize = expectedFileSize;
    
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyFileStarted(data, currentFileSize, expectedFileSize);
                    }catch(Exception e2){
                        
                    }
                }
            }
            
            if (Thread.interrupted()){
                throw new InterruptedException();
            }
            
            byte[] buffer = new byte[8192];
            int loopAmount = 0;
            long sessionDone = 0;
            is = response.getEntity().getContent();
            tempFile.getParentFile().mkdirs();
            os = new FileOutputStream(tempFile, gotPartialFile);
            os = new BufferedOutputStream(os, buffer.length);
            
            boolean breakRequest = false;;
            
            int count;
            long lastProgressUpdateCall = System.currentTimeMillis();
            while ((count = is.read(buffer)) != -1){
                os.write(buffer, 0, count);
                loopAmount += count;
                sessionDone += count;
                
                final long now = System.currentTimeMillis();
                if ((loopAmount > 1024) || (loopAmount > 0) && (lastProgressUpdateCall+1000L < now)){
                    lastProgressUpdateCall = now;
                    for (DownloadProgressMonitor o : observers){
                        try{
                            o.notifyFileProgress(data, loopAmount, sessionDone);
                        }catch(Exception e2){
                        }
                    }
                    loopAmount = 0;
                }
                breakRequest |= abortFlag || Thread.interrupted();
                if (breakRequest){
                    break;
                }
            }

            if (loopAmount > 0){
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyFileProgress(data, loopAmount, sessionDone);
                    }catch(Exception e2){
                    }
                }
            }
            
            buffer = null;
            os.flush();
            os.close();
            os = null;

            if (breakRequest){
                throw new InterruptedException();
            }
            
            // we will abort request, don't close input stream, since it may drain it
            is.close();
            is = null;

            // if we got to the end, we can report file finished ok :)
            if (!tempFile.renameTo(data.target)){
                throw new IOException("Failed to rename temp file to " + data.target);
            }
            {
                final Header lastModifiedHeader = response.getFirstHeader("Last-Modified");
                if (lastModifiedHeader != null){
                    try{
                        final long date = DateUtils.parseDate(lastModifiedHeader.getValue()).getTime();
                        if (date < System.currentTimeMillis() + 24L*60L*60L*1000L){
                            data.target.setLastModified(date);
                        }
                    }catch(Exception e){
                        // ignore
                    }
                }
            }
            isOk = true;
        }catch(Exception e){
            // in case of any error, or user breaking the process - don't read the input stream to the end,
            // since it may be in a corrupted state, or just be very long ;)
            ResponseUtils.abortResponse(response);
            data.exception = e;
            for (DownloadProgressMonitor o : observers){
                try{
                    o.notifyFileFinished(data, e);
                }catch(Exception e2){
                    
                }
            }
        }finally{
            filesOnHold.remove(data.target);
            downloadTask.currentRequest = null;
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
            ResponseUtils.closeResponse(response);
            if (isOk){
                for (DownloadProgressMonitor o : observers){
                    try{
                        o.notifyFileFinished(data, null);
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

    /**
     * Sets the number of worker threads. If the value increases, new threads are started to process 
     * tasks. If the value decreases, existing tasks are allowed to finish, and then their threads are
     * released
     * @param numOfWorkerThreads
     */
    public void setNumOfWorkerThreads(final int numOfWorkerThreads)
    {
        if (numOfWorkerThreads < 0){
            throw new IllegalArgumentException("numOfWorkerThreads=" + numOfWorkerThreads + " < 0");
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

    public boolean isPaused()
    {
        return paused;
    }

    /**
     * Pauses or unpauses the downloader. In paused state, no new tasks will start, but currently running
     * tasks will continue their work. This functionality is intended only to temporarly pause new tasks
     * from starting, in case of network outage or similar, temporary problems. To permanently pause the
     * downloader, {@link #setNumOfWorkerThreads(int)} to 0.
     * @param paused
     */
    public synchronized void setPaused(boolean paused)
    {
        this.paused = paused;
        this.notifyAll();
    }

}
