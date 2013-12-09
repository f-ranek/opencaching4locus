package org.bogus.domowygpx.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.logging.Log;
import org.bogus.logging.LogFactory;

import android.content.Context;
import android.os.Environment;
public class TargetDirLocator
{
    private final static Log logger = LogFactory.getLog(TargetDirLocator.class);

    private static final String LOCUS_ROOT_DIR_NAME = "Locus";
    private static final String GPX_ROOT_DIR_NAME = "gpx";

    private final Pattern spacePattern = Pattern.compile("[ \t]+");
    private Set<File> mountPoints;
    
    @SuppressWarnings("unused")
    private final Context context;
    
    public TargetDirLocator(Context context)
    {
        this.context = context;
    }

    protected boolean checkDirectory(File f)
    {
        return f != null && f.exists() && f.canRead() && f.isDirectory();
    }
    
    protected Set<File> getMountPoints()
    {
        if (mountPoints == null){
            final Set<File> mountPoints = new LinkedHashSet<File>();
            // parse /etc/vold.fstab
            parseVold(mountPoints);
            // parse mounted filesystems
            parseMountPoints(mountPoints);
            // fallback to android sd card location
            File f = Environment.getExternalStorageDirectory();
            if (checkDirectory(f)){
                mountPoints.add(f);
            }
            //f = context. // Environment.getExternalStorageAndroidDataDir();
            //if (checkDirectory(f)){
            //    mountPoints.add(f);
            //}
            this.mountPoints = Collections.unmodifiableSet(mountPoints);
        }
        return mountPoints;
    }
    
    /**
     * Returns all potential Locus directories, ordered by preference match. 
     * Returns an empty collection, if no matches found
     * @return
     */
    public List<File> locateLocus()
    {
        final List<Object[]> locusDirs = new ArrayList<Object[]>();
        for (File mp : getMountPoints()){
            long stamp = isLocusHere(mp);
            if (stamp > 0){
                locusDirs.add(new Object[]{stamp, new File(mp, LOCUS_ROOT_DIR_NAME)});
            }
        }
        
        if (locusDirs.isEmpty()){
            return Collections.emptyList();
        }
        
        Collections.sort(locusDirs, new Comparator<Object[]>(){

            @Override
            public int compare(Object[] o1, Object[] o2)
            {
                return ((Long)o2[0]).compareTo((Long)o1[0]);
            }});
        
        final List<File> result = new ArrayList<File>(locusDirs.size());
        for (Object[] lds : locusDirs){
            result.add((File)lds[1]);
        }
        return result;
    }
    
    /**
     * Returns all potential save directories, ordered by preference match
     * @return
     */
    public List<File> locateSaveDirectories()
    {
        final List<File> result = new ArrayList<File>();

        final Set<File> mp = getMountPoints();
        final Set<File> filesToSkip = new HashSet<File>(2);
        
        final File sdCardRoot = Environment.getExternalStorageDirectory();
        if (checkDirectory(sdCardRoot) && sdCardRoot.canWrite()){
            filesToSkip.add(sdCardRoot);
            result.add(new File(sdCardRoot, GPX_ROOT_DIR_NAME));
        }
        final File dataDir = Environment.getDataDirectory();
        if (checkDirectory(dataDir) && dataDir.canWrite()){
            filesToSkip.add(dataDir);
            result.add(new File(dataDir, GPX_ROOT_DIR_NAME));
        }
        
        for (File f : mp){
            if (!filesToSkip.contains(f) && f.canWrite()){
                result.add(new File(f, GPX_ROOT_DIR_NAME));
            }
        }
        return result;
    }
    
    protected long isLocusHere(File root)
    {
        File locus = new File(root, LOCUS_ROOT_DIR_NAME);
        if (!locus.exists() || !locus.canRead() || !locus.canWrite()){
            return 0;
        }
        File mapItems = new File(locus, "mapItems");
        if (!mapItems.exists() || !mapItems.canRead() || !mapItems.canWrite()){
            return 0;
        }
        final long[] lastTimeStamp = new long[1];
        FileUtils.listFiles(locus, new IOFileFilter(){

            @Override
            public boolean accept(File file)
            {
                lastTimeStamp[0] = file.lastModified();
                return true;
            }

            @Override
            public boolean accept(File dir, String name)
            {
                return accept(new File(dir, name));
            }}, TrueFileFilter.TRUE);
        
        return lastTimeStamp[0];
    }
    
    protected void parseMountPoints(Set<File> result)
    {
        InputStream is = null;
        try{
            File f = new File("/proc/mounts");
            if (!f.exists() || !f.canRead()){
                return ;
            }
            is = new FileInputStream(f);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is), 128);
            String line;
            while ((line = reader.readLine()) != null){
                if (line.length() == 0){
                    continue;
                }
                final String[] data = spacePattern.split(line);
                // 0 device
                // 1 mount point
                // 2 file system
                // 3 and so on params
                if (data.length >= 2){
                    File mountPoint = new File(data[1]);
                    if (checkDirectory(mountPoint)){
                        result.add(mountPoint);
                    }
                }
            }
            reader.close();
        }catch(Exception e){
            logger.warn("Failed to parse /proc/mounts", e);
        }finally{
            IOUtils.closeQuietly(is);
        }
    }


    protected void parseVold(Set<File> result)
    {
        InputStream is = null;
        try{
            File f = new File("/etc/vold.fstab");
            if (!f.exists() || !f.canRead()){
                return ;
            }
            is = new FileInputStream(f);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is), 128);
            String line;
            while ((line = reader.readLine()) != null){
                if (line.length() == 0 || line.startsWith("#")){
                    continue;
                }
                if (!line.startsWith("dev_mount")){
                    continue;
                }
                final String[] data = spacePattern.split(line);
                // 0 dev_mount
                // 1 device id
                // 2 mount point
                // 3 and so on params
                if (data.length >= 3){
                    File mountPoint = new File(data[2]);
                    if (checkDirectory(mountPoint)){
                        result.add(mountPoint);
                    }
                }
            }
            reader.close();
        }catch(Exception e){
            logger.warn("Failed to parse /etc/vold.fstab", e);
        }finally{
            IOUtils.closeQuietly(is);
        }
    }
}
