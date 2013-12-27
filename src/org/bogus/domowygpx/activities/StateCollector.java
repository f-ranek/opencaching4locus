package org.bogus.domowygpx.activities;

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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bogus.domowygpx.services.DumpableDatabase;
import org.bogus.domowygpx.services.FilesDownloaderService;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.services.LocalBinderIntf;
import org.bogus.geocaching.egpx.R;
import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarOutputStream;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
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
                    for (File file : items){
                        File target = new File(rootDir, file.getName());
                        FileUtils.copyFile(file, target, true);
                        files.add(target);
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
    
    @SuppressLint("SimpleDateFormat")
    private void saveDeviceInfo(List<File> files, File root, long errorTimeStamp)
    throws IOException
    {
        File output = new File(root, "info.txt");
        
        StringBuilder sb = new StringBuilder(512);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sb.append("Data bieżąca: ").append(sdf.format(new Date())).append('\n');
        if (errorTimeStamp > 0){
            sb.append("Data błędu: ").append(sdf.format(new Date(errorTimeStamp))).append('\n');
        }
        try{
            final String packageName = context.getPackageName();
            sb.append("Aplikacja: ");
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
        
        sb.append("\n\nID urządzenia: ").append(Secure.getString(context.getContentResolver(), Secure.ANDROID_ID));

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
            Log.i(LOG_TAG, "Saved " + name);
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
        
        SimpleDateFormat sdf = new SimpleDateFormat("'awaryjniejszy-gpx-state-'yyyyMMdd-hhmmss'.tgz'");
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
            
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(fileName));
            intent.setType("application/octet-stream");
            context.sendBroadcast(intent);
            
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
        final File root = new File(context.getCacheDir(), "dump_" + now);
        root.mkdirs();
        collectLogCatOutput(new ArrayList<File>(1), root);
        File info = new File(context.getCacheDir(), "emergency_dump.state");
        OutputStream os = new FileOutputStream(info);
        os.write((String.valueOf(now) + '\n').getBytes());
        os.close();
    }
    
    /**
     * Creates and saves offline (before application start) data dump
     * @param timestamp
     * @return
     */
    @SuppressLint( "SimpleDateFormat" )
    public File dumpDataOffline(long timestamp)
    {
        File info = new File(context.getCacheDir(), "emergency_dump.state");
        info.delete();
        
        final File root = new File(context.getCacheDir(), "dump_" + timestamp);
        if (!root.exists() || !root.isDirectory()){
            return null;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("'awaryjniejszy-gpx-x-state-'yyyyMMdd-hhmmss'.tgz'");
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
            flush(tos, files, root);
            collectOfflineServicesState(files, root);
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
        intent.putExtra(Intent.EXTRA_TEXT, context.getResources().getString(R.string.infoDeveloperEmailText, extractFileName(file)));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"awaryjniejszy-gpx@10g.pl"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Awaryjniejszy GPX - dev");
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        context.startActivity(Intent.createChooser(intent, null));
    }
}