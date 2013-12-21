package org.bogus.domowygpx.html;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.bogus.domowygpx.gpx.GpxState;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.domowygpx.utils.Hex;
import org.bogus.logging.LogFactory;

import android.util.Base64InputStream;


public class ImageUrlProcessor implements ImageSourceResolver
{
    private final static Log logger = LogFactory.getLog(ImageUrlProcessor.class);

    private final static List<Pattern> pathCleanupPatterns = new ArrayList<Pattern>(); 
    private final static List<Pattern> internalPathPatterns = new ArrayList<Pattern>();
    private final static Pattern doubleSlashRemovalPattern = Pattern.compile("/{2,}");
    private final static Pattern extensionPattern = Pattern.compile("\\.([A-Z0-9]{1,6})$", Pattern.CASE_INSENSITIVE);

    private final static Set<String> allowedImageExtensions = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "png", "bmp", "jpg", "jpeg", "gif"
        )));
    
    private final static List<String> opencachingDomains = new ArrayList<String>(); 
    
    static void loadPathPatterns(String fileName, List<Pattern> target)
    {
        String line = null;
        InputStream is = null;
        try{
            is = ImageUrlProcessor.class.getResourceAsStream(fileName);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is), 128);
            while ((line = reader.readLine()) != null){
                if (line.length() > 0 && !line.startsWith("#")){
                    target.add(Pattern.compile(line));
                }
            }
            reader.close();
        }catch(Exception e){
            logger.error("Failed to read config from " + fileName + ", offending line='" + line + "'", e);
        }finally{
            IOUtils.closeQuietly(is);
        }
        
    }
    
    static {
        InputStream is = null;
        try{
            is = ImageUrlProcessor.class.getResourceAsStream("oc_domains.txt");
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is), 128);
            String line;
            while ((line = reader.readLine()) != null){
                if (line.length() > 0 && !line.startsWith("#")){
                    opencachingDomains.add(line);
                }
            }
            reader.close();
        }catch(Exception e){
            logger.error("Failed to read OpenCaching domains from config", e);
        }finally{
            IOUtils.closeQuietly(is);
        }
        loadPathPatterns("path_clreanups.txt", pathCleanupPatterns);
        loadPathPatterns("internal_paths.txt", internalPathPatterns);
    }
    
    private String virtualLocation;
    private File targetBase;
    private boolean downloadImages = true;
    private boolean extractDataImages = true;
    private boolean downloadCommentImages;
    
    private GpxState gpxState;
    private List<FileData> queue;
    
    public ImageUrlProcessor(GpxState gpxState, File targetBase, String virtualLocation)
    {
        this.gpxState = gpxState;
        this.queue = new ArrayList<FileData>();
        
        this.targetBase = targetBase;
        this.virtualLocation = virtualLocation;
        
        if (!this.virtualLocation.endsWith("/")){
            this.virtualLocation = this.virtualLocation + "/";
        }
    }

    public int getRawSize()
    {
        return queue.size();
    }
    
    public List<FileData> getDataFiles()
    {
        final Map<URI, FileData> deduplicator = new LinkedHashMap<URI, FileData>(queue.size());
        for (FileData id : queue){ 
            deduplicator.put(id.source, id);
        }
        final List<FileData> result = new ArrayList<FileData>(deduplicator.size());
        result.addAll(deduplicator.values());
        
        Collections.sort(result, new Comparator<FileData>(){

            @Override
            public int compare(FileData o1, FileData o2)
            {
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
        
        return result;   
    }
    
    protected String cleanupInternalPath(URI uri)
    {
        String path = uri.getPath();
        path = path.replace('\\', '/');
        for (Pattern p : pathCleanupPatterns){
            path = p.matcher(path).replaceAll("/");
        }
        return path;
    }

    protected String getPathForCacheCode(String cacheCode)
    {
        final int len = cacheCode.length();
        if (len <= 3){
            return cacheCode;
        }
        StringBuilder sb = new StringBuilder(len+4);
        sb.append(cacheCode.charAt(len-1)).append('/');
        sb.append(cacheCode.charAt(len-2)).append('/');
        sb.append(cacheCode);
        return sb.toString(); 
    }
    
    protected String cleanupPath(URI uri)
    {
        StringBuilder sb;
        try{
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(uri.toASCIIString().getBytes());
            final byte[] data = md.digest();
            sb = new StringBuilder(data.length*2 + 10);
            sb.append(Hex.encodeHex(data));
        }catch(NoSuchAlgorithmException nsae){
            sb = new StringBuilder(20);
            sb.append(uri.hashCode());
            sb.append(uri.getHost().hashCode());
            sb.append(uri.getPath().hashCode());
        }
        final Matcher m = extensionPattern.matcher(uri.getPath());
        if (m.find()){
            String ext = m.group(1).toLowerCase(Locale.US);
            if (allowedImageExtensions.contains(ext)){
                sb.append('.').append(ext);
            }
        }
        return sb.toString();
    }
    
    protected boolean isUriTrusted(URI uri)
    {
        if (uri == null || uri.getHost() == null){
            return false;
        }
        try{
            final String host = uri.getHost();
            for (String trustedUrl : opencachingDomains){
                if (host.endsWith(trustedUrl)){
                    return true;
                }
            }
        }catch(Exception e){
        }
        return false;
    }
    
    protected String getExtensionForContentType(String contentType)
    {
        if (contentType.startsWith("image/")){
            return contentType.substring(6);
        } else {
            return null;
        }
    }
    
    protected String extractDataImage(final String src, final String currentCacheCode)
    {
        if (!src.startsWith("data:")){
            logger.warn(currentCacheCode + ": incorrect data: uri");
            return src;
        }
        int commaIdx = src.indexOf(',');
        if (commaIdx < 5){
            logger.warn(currentCacheCode + ": incorrect data: uri, " + src.substring(0, Math.max(25, src.length())));
            return src;
        }
        
        try{
            final String[] parts = src.substring(5,commaIdx).split(";");
            boolean isBase64 = parts.length > 0 && parts[parts.length-1].equals("base64");
            String mimeType = null;
            String charset = null;
            if (parts.length > 0 && !parts[0].equals("base64") && !parts[0].startsWith("charset=")){
                mimeType = parts[0];
            }
            if (!isBase64){
                for (String p : parts){
                    if (p.startsWith("charset=")){
                        charset = p.substring(8);
                        if (charset.length() > 0){
                            break;
                        } else {
                            charset = null;
                        }
                    }
                }
            }
            
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final StringBuilder targetFileName = new StringBuilder(64);
            final String localPath = getPathForCacheCode(currentCacheCode);
            targetFileName.append(localPath);
            targetFileName.append('/');
            
            final String rawData = src.substring(commaIdx+1);
            final File tempFile = new File(targetBase, localPath + "/data-" + System.currentTimeMillis() + "_" + Math.abs(System.identityHashCode(this)) + ".tmp");
            tempFile.getParentFile().mkdirs();
            
            InputStream is = null;
            OutputStream os = null;
            try{
                final StringReader sr = new StringReader(rawData);
                if (isBase64){
                    is = new InputStream()
                    {
                        private final char[] buff = new char[128];
                        @Override
                        public int read(byte b[], int off, int len) 
                        throws IOException {
                            final int len2 = Math.min(len,  buff.length);
                            final int len3 = sr.read(buff,0,len2);
                            if (len3 < 0){
                                return -1;
                            }
                            for (int i=0, j=off; i<len3; i++, j++){
                                b[j] = (byte)buff[i];
                            }
                            return len3;
                        }
                        @Override
                        public int read() throws IOException
                        {
                            int ch = sr.read();
                            if (ch < 0){
                                return -1;
                            } else {
                                byte result = (byte)ch;
                                return result;
                            }
                        }
                    };
                    is = new Base64InputStream(is, 0);
                    os = new FileOutputStream(tempFile);
                    os = new BufferedOutputStream(os, 8192);
                    DigestOutputStream dos = new DigestOutputStream(os, md);
                    IOUtils.copy(is, dos);
                    dos.flush();
                    dos.close();
                } else {
                    os = new FileOutputStream(tempFile);
                    os = new BufferedOutputStream(os, 8192);
                    DigestOutputStream dos = new DigestOutputStream(os, md);
                    if (charset == null){
                        charset = "UTF-8";
                    }
                    Writer w = new OutputStreamWriter(dos, charset);
                    IOUtils.copy(sr, w);
                    w.flush();
                    w.close();
                }
                targetFileName.append(Hex.encodeHex(md.digest()));
            }finally{
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(os);
            }
    
            final String ext = mimeType == null ? null : getExtensionForContentType(mimeType);
            if (ext != null){
                targetFileName.append('.').append(ext);
            }
            
            // rename temp file
            final File targetFile = new File(targetBase, targetFileName.toString());
            targetFile.getParentFile().mkdirs();
            targetFile.delete();
            tempFile.renameTo(targetFile);
            
            if (logger.isDebugEnabled()){
                logger.debug(currentCacheCode + ": saved data: at " + targetFile);
            }

            targetFileName.insert(0, virtualLocation);
            return targetFileName.toString();
        }catch(IOException ioe){
            logger.warn(currentCacheCode + ": got IO Exception", ioe);
            return src;
        }catch(NoSuchAlgorithmException nsae){
            logger.warn(currentCacheCode + ": got MD5 Exception", nsae);
            return src;
        }
    }
    
    @Override
    public String processImgSrc(String src, int sourcePlaceCode)
    {
        final String currentCacheCode = gpxState.getCurrentCacheCode(); 
        try{
            //logger.debug(currentCacheCode + ": found img src=" + src);
            {
                final URI uri;
                if (src.startsWith("\"") && src.endsWith("\"")){
                    uri = new URI(src.substring(1, src.length()-2));
                } else
                if (src.startsWith("data:")){
                    if (extractDataImages){
                        String res = extractDataImage(src, currentCacheCode);
                        return res;
                    } else {
                        return src;
                    }
                } else 
                {
                    uri = new URI(src);
                }
                if (uri.getPath() == null || uri.getPath().equals("/")){
                    logger.warn(currentCacheCode + ": no path specified: " + src);
                    return src;
                }
            }
            final URI trustedUri;
            final URI resolvedUri;
            final String gpxUrl = gpxState.getGpxUrl();
            if (gpxUrl == null || gpxUrl.length() == 0){
                resolvedUri = new URI(src);
            } else {
                resolvedUri = new URL(new URL(gpxUrl), src).toURI();
            }

            if (resolvedUri.getHost() == null){
                logger.warn(currentCacheCode + ": no host specified: " + src);
                return src;
            }
            
            if (!downloadImages || !downloadCommentImages && sourcePlaceCode == SOURCE_COMMENT){
                return resolvedUri.toASCIIString();
            }
            
            if (isUriTrusted(resolvedUri)){
                trustedUri = resolvedUri;
            } else {
                trustedUri = null;
            }
            
            final FileData imageData = new FileData();
            imageData.cacheCode = currentCacheCode;
            String localPath;
            if (trustedUri != null){
                // tinymce images and so on
                localPath = cleanupInternalPath(trustedUri);
                boolean isInternal = false;
                for (Pattern p : internalPathPatterns){
                    if (p.matcher(trustedUri.getPath()).find()){
                        isInternal = true;
                        break;
                    }
                }
                if (isInternal){
                    localPath = ".shared/" + localPath;
                    imageData.priority = 250;
                } else {
                    localPath = getPathForCacheCode(currentCacheCode) + "/" + localPath;
                    imageData.priority = 10 + 10 * sourcePlaceCode;
                }
                localPath = doubleSlashRemovalPattern.matcher(localPath).replaceAll("/");
                imageData.source = trustedUri;
            } else {
                // external resources
                localPath = cleanupPath(resolvedUri);
                localPath = getPathForCacheCode(currentCacheCode) + "/" + localPath;
                localPath = doubleSlashRemovalPattern.matcher(localPath).replaceAll("/");
                imageData.source = resolvedUri;
                imageData.priority = 15 + 10 * sourcePlaceCode;
            }
            imageData.virtualTarget = virtualLocation + localPath;
            imageData.target = new File(targetBase, localPath);
            
            queue.add(imageData);
            //logger.debug("-> " + imageData.virtualTarget);
            return imageData.virtualTarget;
        }catch(Exception use){
            logger.warn(currentCacheCode + ": Invalid src attribute=" + src, use);
            return src;
        }
         
        
    }

    public boolean isDownloadImages()
    {
        return downloadImages;
    }

    public void setDownloadImages(boolean downloadImages)
    {
        this.downloadImages = downloadImages;
    }

    public boolean isDownloadCommentImages()
    {
        return downloadCommentImages;
    }

    public void setDownloadCommentImages(boolean downloadCommentImages)
    {
        this.downloadCommentImages = downloadCommentImages;
    }

    public boolean isExtractDataImages()
    {
        return extractDataImages;
    }

    public void setExtractDataImages(boolean extractDataImages)
    {
        this.extractDataImages = extractDataImages;
    }

}
