package org.bogus.domowygpx.application;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.domowygpx.services.DumpableDatabase;
import org.bogus.domowygpx.services.FilesDownloaderService;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.services.LocalBinderIntf;
import org.bogus.domowygpx.utils.DumpDatabase;
import org.bogus.domowygpx.utils.HttpClientFactory;
import org.bogus.domowygpx.utils.HttpClientFactory.CreateHttpClientConfig;
import org.bogus.geocaching.egpx.R;
import org.bogus.utils.io.MemoryBufferStream;
import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarOutputStream;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings.Secure;
import android.util.Log;

public class StateCollector
{
    private final static String LOG_TAG = "StateCollector";
    final Context context;

    @SuppressWarnings("unchecked")
    final static Class<? extends DumpableDatabase>[] services = new Class[]{
        GpxDownloaderService.class, FilesDownloaderService.class
    };
    
    public StateCollector(Context context)
    {
        this.context = context;
    }

    private void collectLogCatOutput(List<File> files, File root)
    {
        File output = null;
        OutputStream os = null;
        InputStream is = null;
        try{
            Process process = Runtime.getRuntime().exec("logcat -d -v long");
            output = new File(root, "logcat.log");
            is = process.getInputStream();
            os = new FileOutputStream(output);
            IOUtils.copy(is, os);
            files.add(output);
        }catch(Exception e){
            Log.e(LOG_TAG, "Failed to dump logcat", e);
            if (output != null){
                output.delete();
            }
        }finally{
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }
    }

    private void collectLogCatOutput2(List<File> files, File root, String bufferName)
    {
        final int lines = 150;
        File output = null;
        OutputStream os = null;
        InputStream is = null;
        try{
            Process process = Runtime.getRuntime().exec("logcat -d -v long -b " + bufferName);
            output = new File(root, "logcat-" + bufferName + ".log");
            is = process.getInputStream();
            
            final List<MemoryBufferStream> mbss = new ArrayList<MemoryBufferStream>(lines);
            MemoryBufferStream mbs = new MemoryBufferStream(32);
            int len;
            final byte[] buffer = new byte[32];
            while((len = is.read(buffer)) != -1){
                int start = 0;
                for (int i=0; i<len; i++){
                    byte ch = buffer[i];
                    if (ch == '\n' || ch == '\r'){
                        mbs.write(buffer, start, i-start);
                        start = i+1;
                        if (mbs.length() != 0){
                            MemoryBufferStream mbs2 = null;
                            if (mbss.size() >= lines){
                                mbs2 = mbss.remove(0);
                                mbs2.reset();
                            } else {
                                mbs2 = new MemoryBufferStream(32);
                            }
                            mbss.add(mbs);
                            mbs = mbs2;
                        }
                    }
                }
                mbs.write(buffer, start, len-start);
            }
            if (mbs.length() != 0){
                if (mbss.size() >= lines){
                    mbss.remove(0);
                }
                mbss.add(mbs);
            }

            if (!mbss.isEmpty()){
                os = new FileOutputStream(output);
                os = new BufferedOutputStream(os, 512);
                for (MemoryBufferStream mbsi : mbss){
                    mbsi.writeTo(os);
                    os.write('\n');
                }
                os.flush();
                files.add(output);
            }
        }catch(Exception e){
            Log.e(LOG_TAG, "Failed to dump logcat buffer=" + bufferName, e);
            if (output != null){
                output.delete();
            }
        }finally{
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }
    }
    
    private void collectOfflineServicesState(List<File> files, File rootDir)
    {
        rootDir = new File(rootDir, "databases");
        rootDir.mkdirs();
        
        for (int i=0; i<services.length; i++){
            try{
                final DumpableDatabase dd = services[i].newInstance();
                Log.i(LOG_TAG, "Dumping " + services[i].getSimpleName() + " state");
                List<File> items = dd.getDatabaseFileNames(context);
                if (items != null){
                    final DumpDatabase ddu = new DumpDatabase();
                    for (File file : items){
                        File target = new File(rootDir, file.getName());
                        FileUtils.copyFile(file, target, true);
                        files.add(target);
                    }
                    for (File file : items){
                        if (ddu.isFileNameSuffixed(file)){
                            continue;
                        }
                        SQLiteDatabase database = null;
                        File target = new File(rootDir, file.getName() + ".xml");
                        try{
                            // try to open DB, and dump it's content in a human-readable format
                            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), 
                                null, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                            ddu.dumpDatabase(database, target);
                            files.add(target);
                        }catch(Exception e){
                            Log.e(LOG_TAG, "Failed to dump " + file + " in human-readable format");
                            target.delete();
                        }finally{
                            if (database != null){
                                try{
                                    database.close();
                                }catch(Exception e){
                                    Log.e(LOG_TAG, "Failed to close database: " + file);
                                }
                            }
                        }
                    }
                }
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to dump " + services[i].getSimpleName() + " state", e);
            }
        }
    }

    private void collectOnlineServicesState(List<File> files, File rootDir)
    {
        final DumpableDatabase[] dumpableDatabases = new DumpableDatabase[services.length];
        final ServiceConnection[] serviceConnections = new ServiceConnection[services.length];
        final Semaphore ready = new Semaphore(0);
        
        for (int i=0; i<services.length; i++){
            final int idx = i;
            serviceConnections[idx] = new ServiceConnection()
            {
                @Override
                public void onServiceDisconnected(ComponentName name)
                {
                    synchronized(dumpableDatabases){
                        services[idx] = null;
                    }
                }
                
                @Override
                public void onServiceConnected(ComponentName name, IBinder service)
                {
                    synchronized(dumpableDatabases){
                        @SuppressWarnings("unchecked")
                        DumpableDatabase dd = ((LocalBinderIntf<DumpableDatabase>)service).getService();
                        dumpableDatabases[idx] = dd;
                    }
                    ready.release();
                }
            };
        }
        
        for (int i=0; i<services.length; i++){
            context.bindService(new Intent(context, services[i]), serviceConnections[i], Context.BIND_AUTO_CREATE);
        }
        try{
            boolean interrupted = Thread.interrupted();
            try{
                ready.tryAcquire(services.length, 15, TimeUnit.SECONDS);
            }catch(InterruptedException ie){
                interrupted = true;
            }
            if (interrupted){
                Thread.currentThread().interrupt();
            }
            
            
            rootDir = new File(rootDir, "databases");
            rootDir.mkdirs();
            
            for (int i=0; i<services.length; i++){
                try{
                    final DumpableDatabase dd;
                    synchronized(dumpableDatabases){
                        dd = dumpableDatabases[i];
                    }
                    if (dd != null){
                        Log.i(LOG_TAG, "Dumping " + services[i].getSimpleName() + " state");
                        List<File> items = dd.dumpDatabase(rootDir);
                        if (items != null){
                            files.addAll(items);
                        }
                    }
                }catch(Exception e){
                    Log.e(LOG_TAG, "Failed to dump " + services[i].getSimpleName() + " state", e);
                }
            }
        }finally{
            for (int i=0; i<services.length; i++){
                context.unbindService(serviceConnections[i]);
            }
        }
    }    
    
    private File collectSharedPrefs(File name, File rootDir)
    {
        // TODO: we should care about file synchronization here 
        // TODO: remove oauth private data
        try{
            File target = new File(rootDir, "shared_pref_" + name.getName());
            FileUtils.copyFile(name, target);
            return target;
        }catch(IOException ioe){
            Log.e(LOG_TAG, "Failed to dump shared preferences, file=" + name, ioe);
            return null;
        }
    }

    private void collectSharedPrefs(List<File> files, File rootDir)
    {
        File dir = new File(context.getFilesDir(), "../shared_prefs/");
        File[] children = dir.listFiles();
        if (children != null){
            for (File file : children){
                File target = collectSharedPrefs(file, rootDir);
                if (target != null){
                    files.add(target);
                }
            }
        }
    }

    private void collectTempFiles(List<File> files, File root)
    {
        root = new File(root, "cache");
        root.mkdirs();
        File dir = context.getCacheDir();
        File[] children = dir.listFiles();
        if (children != null){
            for (File file : children){
                if (!file.isFile()){
                    continue;
                }
                File target = null;
                InputStream is = null;
                OutputStream os = null;
                String name = file.getName();
                try{
                    boolean done = false;
                    if (name.endsWith(".gz")){
                        try{
                            target = new File(root, name.substring(0, name.length()-3)); 
                            os = new FileOutputStream(target);
                            os = new BufferedOutputStream(os, 4096);
                            is = new FileInputStream(file);
                            is = new GZIPInputStream(is);
                            IOUtils.copy(is, os);
                            done = true;
                        }catch(IOException ioe){
                            Log.e(LOG_TAG, "Failed to copy file=" + name, ioe);
                        }
                    }
                    if (!done){
                        target = new File(root, name);
                        FileUtils.copyFile(file, target, true);
                    }
                    files.add(target);
                }catch(Exception e){
                    Log.e(LOG_TAG, "Failed to copy file=" + name, e);
                    IOUtils.closeQuietly(os);
                    target.delete();
                }finally{
                    IOUtils.closeQuietly(is);
                    IOUtils.closeQuietly(os);
                }
            }
        }
    }
    
    private void saveAppInfo(String packageName, StringBuilder sb)
    {
        try{
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            if (info == null)
                return ;
            sb.append(packageName);
            sb.append(": ").append(info.versionName).append(" (").append(info.versionCode).append(')');
            ApplicationInfo appInfo = info.applicationInfo;
            if (!appInfo.enabled){
                sb.append(" (disabled)");
            }
            sb.append('\n');
        }catch(NameNotFoundException nnfe){
            
        }
         
    }
    
    @SuppressLint("SimpleDateFormat")
    private void saveDeviceInfo(List<File> files, File root, long errorTimeStamp)
    throws IOException
    {
        File output = new File(root, "info.txt");
        
        StringBuilder sb = new StringBuilder(512);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sb.append("Current date: ").append(sdf.format(new Date())).append('\n');
        if (errorTimeStamp > 0){
            sb.append("Error date: ").append(sdf.format(new Date(errorTimeStamp))).append('\n');
        }
        try{
            final String packageName = context.getPackageName();
            sb.append("Application: ");
            final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            sb.append(packageInfo.versionName).append(" (").append(org.bogus.geocaching.egpx.BuildInfo.GIT_VERSION).append(")");
        }catch(NameNotFoundException nnfe){
            // should not happen ;)
        }
        
        sb.append("\nSystem:");
        sb.append("\nOS Version: ").append(System.getProperty("os.version")).append(" (").append(android.os.Build.VERSION.INCREMENTAL).append(")");
        sb.append("\nOS API Level: ").append(android.os.Build.VERSION.SDK_INT);
        sb.append("\nDevice: ").append(android.os.Build.DEVICE);
        sb.append("\nModel (and Product): ").append(android.os.Build.MODEL).append(" (").append(android.os.Build.PRODUCT).append(")");
        
        sb.append("\n\nDevice ID: ").append(Secure.getString(context.getContentResolver(), Secure.ANDROID_ID));

        sb.append("\n\n");
        saveAppInfo("menion.android.locus", sb);
        saveAppInfo("menion.android.locus.pro", sb);
        
        OutputStream os = null;
        try{
            os = new FileOutputStream(output);
            os.write(sb.toString().getBytes("UTF-8"));
        }finally{
            IOUtils.closeQuietly(os);
        }
        
        files.add(output);
    }
    
    private void flush(TarOutputStream tos, List<File> files, File rootDir)
    throws IOException
    {
        int len = rootDir.getPath().length();
        if (!rootDir.getPath().endsWith("/")){
            len++;
        }
        for (File file : files){
            final String name = file.getPath().substring(len);
            final InputStream is = new FileInputStream(file);
            try{
                tos.putNextEntry(new TarEntry(file, name));
                IOUtils.copy(is, tos);
            }finally{
                IOUtils.closeQuietly(is);
                file.delete();
            }
            Log.i(LOG_TAG, "Flushed " + name);
        }
        files.clear();
    }
    
    /**
     * Creates and saves online data dump. Returns dump filename. This method can be called only from
     * a worker thread (not from main application thread!) 
     * @return
     */
    @SuppressLint( "SimpleDateFormat" )
    public File dumpDataOnline()
    {
        final File root = new File(context.getCacheDir(), "dump_" + System.currentTimeMillis());
        root.mkdirs();
        
        SimpleDateFormat sdf = new SimpleDateFormat("'awaryjniejszy-gpx-state-'yyyyMMdd-HHmmss'.tgz'");
        final String name = sdf.format(new Date());

        final List<File> files = new ArrayList<File>();

        OutputStream os = null;
        File fileName = null;
        try{
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir.isDirectory() && dir.canRead() && dir.canWrite()){
                fileName = new File(dir, name).getAbsoluteFile();
                os = new FileOutputStream(fileName);
            } else {
                Log.w(LOG_TAG, "SD card not available");
                return null;
            }
            Log.i(LOG_TAG, "Saving app state to file=" + fileName);
            os = new GZIPOutputStream(os, 16*1024);
            TarOutputStream tos = new TarOutputStream(os); 
            
            saveDeviceInfo(files, root, 0);
            collectSharedPrefs(files, root);
            flush(tos, files, root);
            collectOnlineServicesState(files, root);
            flush(tos, files, root);
            collectLogCatOutput(files, root);
            flush(tos, files, root);
            collectTempFiles(files, root);
            flush(tos, files, root);
            tos.flush();
            tos.close();
            Log.i(LOG_TAG, "Done");
            
            return fileName;
        }catch(IOException ioe){
            Log.e(LOG_TAG, "Failed to prepare state dump", ioe);
            if (fileName != null){
                fileName.delete();
            }
            return null;
        }finally{
            IOUtils.closeQuietly(os);
            try{
                FileUtils.deleteDirectory(root);
            }catch(Exception e2){
                Log.w(LOG_TAG, "Failed to cleanup directory", e2);
            }
        }
    }  
    
    /**
     * Gets state of an offline, emergency dump
     * @return 0, if no dump is present, otherwise dump timestamp, to be passed to {@link #dumpDataOffline(long)}
     */
    public long hasOfflineDump()
    {
        final File info = new File(context.getCacheDir(), "emergency_dump.state");
        try{
            
            if (info.exists()){
                InputStream is = new FileInputStream(info);
                byte[] data = new byte[24];
                int len = is.read(data);
                is.close();
                String ts = new String(data,0,len).trim();
                long timestamp = Long.parseLong(ts);
                final File root = new File(context.getCacheDir(), "dump_" + timestamp);
                if (root.exists() && root.isDirectory()){
                    return timestamp;
                }
            }
            return 0;
        }catch(Exception e){
            Log.e(LOG_TAG, "Failed to read emergency_dump.state", e);
            return 0;
        }
    }
    
    /**
     * Creates emergency dump
     * @throws IOException
     */
    public void createEmergencyDump()
    throws IOException
    {
        final long now = System.currentTimeMillis();
        Log.i(LOG_TAG, "Creating emergency dump_" + now);
        final File root = new File(context.getCacheDir(), "dump_" + now);
        root.mkdirs();
        
        final File info = new File(context.getCacheDir(), "emergency_dump.state");
        OutputStream os = new FileOutputStream(info);
        os.write((String.valueOf(now) + '\n').getBytes());
        os.close();

        collectLogCatOutput(new ArrayList<File>(1), root);
        
        Log.i(LOG_TAG, "Emergency dump saved to " + root);
    }
    
    /**
     * Creates a dump with only minimum amount of logcat data, plus application info
     * @throws IOException
     */
    public void createSmallStateDump()
    throws IOException
    {
        final long now = System.currentTimeMillis();
        Log.i(LOG_TAG, "Creating ssd " + now);
        final File root = new File(context.getCacheDir(), "ssd_" + now);
        root.mkdirs();
        
        List<File> files = new ArrayList<File>(5);
        collectLogCatOutput2(files, root, "main");
        collectLogCatOutput2(files, root, "events");
        saveDeviceInfo(files, root, 0);
    }
    
    public void checkSmallStateDump()
    {
        File file = context.getCacheDir();
        File[] dirFiles = file.listFiles();
        if (dirFiles != null && dirFiles.length > 0){
            final List<File> files = new ArrayList<File>();
            for (File f : dirFiles){
                if (f.getName().startsWith("ssd_")){
                    files.add(f);
                }
            }
            if (!files.isEmpty()){
                AsyncTask<Void, Void, Void> processTask = new AsyncTask<Void, Void, Void>(){
                    HttpClient httpClient;
                    String deviceId;
                    Pattern fileNamePattern;
                    
                    File processDir(File dir)
                    {
                        OutputStream os = null;
                        File outFile = new File(dir.getParentFile(), dir.getName() + ".tgz");
                        try{
                            File[] dirFiles = dir.listFiles();
                            if (dirFiles == null || dirFiles.length == 0){
                                return null;
                            }

                            os = new FileOutputStream(outFile);
                            os = new GZIPOutputStream(os, 16*1024);
                            final TarOutputStream tos = new TarOutputStream(os);
                            for (File file : dirFiles){
                                final String name = file.getName();
                                final InputStream is = new FileInputStream(file);
                                try{
                                    tos.putNextEntry(new TarEntry(file, name));
                                    IOUtils.copy(is, tos);
                                }finally{
                                    IOUtils.closeQuietly(is);
                                    file.delete();
                                }
                            }
                            tos.flush();
                            tos.close();
                            os.close();
                            return outFile;
                        }catch(Exception e){
                            outFile.delete();
                            Log.e(LOG_TAG, "Failed to process ssd=" + dir.getName(), e);
                            return null;
                        }finally{
                            IOUtils.closeQuietly(os);
                            try{
                                FileUtils.deleteDirectory(dir);
                            }catch(IOException e){
                                Log.e(LOG_TAG, "Failed to cleanup ssd=" + dir.getName(), e);
                            }
                        }
                    }
                    
                    void processFile(File file)
                    {
                        if (fileNamePattern == null){
                            fileNamePattern = Pattern.compile("ssd_([0-9]+)(_([0-9]{1,3}))?(\\.[a-z0-9]{1,6})?", Pattern.CASE_INSENSITIVE);
                        }
                        final String name = file.getName();
                        final Matcher matcher = fileNamePattern.matcher(name);
                        if (!matcher.matches()){
                            file.delete();
                            return ;
                        }
                        
                        if (httpClient == null){
                            CreateHttpClientConfig cfg = new CreateHttpClientConfig(context);
                            cfg.preventCaching = true;
                            httpClient = HttpClientFactory.createHttpClient(cfg);
                            deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
                        }

                        HttpResponse response = null;
                        try{
                            final String uri = "http://bogus.ovh.org/oc4l/ssd.php?deviceId=" 
                                    + deviceId + "&timestamp=" + matcher.group(1);
                            HttpPut put = new HttpPut(uri);
                            put.setEntity(new FileEntity(file, "application/x-compressed"));
                            Log.i(LOG_TAG, "Sending " + name);
                            StatusLine statusLine = null;
                            try{
                                response = httpClient.execute(put);
                                statusLine = response.getStatusLine();
                            }catch(IOException ioe){
                                Log.w(LOG_TAG, "Failed to send ssd=" + name, ioe);
                            }
                            
                            if (statusLine != null && (
                                    statusLine.getStatusCode() == 200 || statusLine.getStatusCode() == 204))
                            {
                                Log.i(LOG_TAG, "File send");
                                file.delete();
                            } else {
                                // check and increment failure count
                                final String counter = matcher.group(3);
                                int cnt = 2;
                                if (counter != null){
                                    cnt = Integer.parseInt(counter);
                                    if (cnt > 5){
                                        Log.i(LOG_TAG, "Deleting file=" + name);
                                        file.delete();
                                    } else {
                                        cnt++;
                                    }
                                }
                                String ext = matcher.group(4);
                                if (ext == null){
                                    ext = "";
                                }
                                String newName = "ssd_" + matcher.group(1) + "_" + cnt + ext;
                                file.renameTo(new File(file.getParent(), newName));
                            }
                        }catch(Exception ex){
                            Log.w(LOG_TAG, "Failed to send ssd=" + name, ex);
                            file.delete();
                        }finally{
                            ResponseUtils.closeResponse(response);
                        }
                    }
                    
                    @Override
                    protected Void doInBackground(Void... params)
                    {
                        try{
                            final List<File> files2 = new ArrayList<File>();
                            for (File f : files){
                                if (f.isDirectory()){
                                    File f2 = processDir(f);
                                    if (f2 != null){
                                        files2.add(f2);
                                    }
                                } else
                                if (f.isFile()){
                                    files2.add(f);
                                }
                            }
                            
                            if (files2.isEmpty()){
                                return null;
                            }
                            
                            // wait up to 5 seconds for an active network connection
                            final ConnectivityManager cm = (ConnectivityManager)
                                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
                            final NetworkInfo ni = cm.getActiveNetworkInfo();
                            if (ni == null || !ni.isConnected()){
                                return null;
                            }
                            for (File f : files2){
                                processFile(f);
                            }
                            
                            return null;
                        }catch(Exception e){
                            Log.e(LOG_TAG, "Failed to process ssd", e);
                            return null;
                        }finally{
                            HttpClientFactory.closeHttpClient(httpClient);
                        }
                    }
                };
                AndroidUtils.executeAsyncTask(processTask);    
            }
        }
    }
    
    /**
     * Creates and saves offline (before application start) data dump
     * @param timestamp
     * @param semaphore Optional semaphore, which is signalled when all of the necessary (offline)
     *          data is ready
     * @return
     */
    @SuppressLint( "SimpleDateFormat" )
    public File dumpDataOffline(long timestamp, Semaphore semaphore)
    {
        File info = new File(context.getCacheDir(), "emergency_dump.state");
        info.delete();
        
        final File root = new File(context.getCacheDir(), "dump_" + timestamp);
        if (!root.exists() || !root.isDirectory()){
            return null;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("'awaryjniejszy-gpx-x-state-'yyyyMMdd-HHmmss'.tgz'");
        final String name = sdf.format(new Date(timestamp));

        final List<File> files = new ArrayList<File>();

        OutputStream os = null;
        File fileName = null;
        try{
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir.isDirectory() && dir.canRead() && dir.canWrite()){
                fileName = new File(dir, name).getAbsoluteFile();
                os = new FileOutputStream(fileName);
            } else {
                Log.w(LOG_TAG, "SD card not available");
                return null;
            }
            Log.i(LOG_TAG, "Saving app state to file=" + fileName);
            os = new GZIPOutputStream(os, 16*1024);
            TarOutputStream tos = new TarOutputStream(os); 
            
            saveDeviceInfo(files, root, timestamp);
            File logcat = new File(root, "logcat.log");
            if (logcat.exists()){
                files.add(logcat);
            } else {
                collectLogCatOutput(files, root);
            }
            collectSharedPrefs(files, root);
            collectOfflineServicesState(files, root);
            if (semaphore != null){
                semaphore.release();
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            }
            flush(tos, files, root);
            collectTempFiles(files, root);
            flush(tos, files, root);
            tos.flush();
            tos.close();
            Log.i(LOG_TAG, "Done");
            return fileName;
        }catch(IOException ioe){
            Log.e(LOG_TAG, "Failed to prepare state dump", ioe);
            if (fileName != null){
                fileName.delete();
            }
            return null;
        }finally{
            IOUtils.closeQuietly(os);
            try{
                FileUtils.deleteDirectory(root);
            }catch(Exception e2){
                Log.w(LOG_TAG, "Failed to cleanup directory", e2);
            }
        }
    }
    
    public String extractFileName(File file)
    {
        final String fileName2 = file.getPath().substring(Environment.getExternalStorageDirectory().getPath().length()+1);
        return fileName2;
    }
    
    public void sendEmail(File file)
    {
        final Uri uri = Uri.fromFile(file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TEXT, context.getResources().getString(
            R.string.infoDeveloperEmailText, 
            extractFileName(file),
            AndroidUtils.formatFileSize((int)(file.length()/1024))
        ));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"awaryjniejszy-gpx@10g.pl"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Awaryjniejszy GPX - dev");
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        context.startActivity(Intent.createChooser(intent, null));
    }
}