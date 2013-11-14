package org.bogus.domowygpx.gpx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.logging.Log;
import org.bogus.domowygpx.html.HTMLProcessor;
import org.bogus.domowygpx.html.ImageSourceResolver;
import org.bogus.geocaching.egpx.BuildConfig;
import org.bogus.logging.LogFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import android.annotation.SuppressLint;

public class GpxProcessor implements GpxState, Closeable
{
    private final static Log logger = LogFactory.getLog(GpxProcessor.class);
    private static final Pattern whitespacePattern1 = Pattern.compile("[\t ]{2,}");
    private static final Pattern whitespacePattern2 = Pattern.compile("(([\t ]*)[\n\r]+([\t ]*))+");
    
    private final static String NS_GROUNDSPEAK = "http://www.groundspeak.com/cache/1/0/1";
    private final static String NS_OPENCACHING = "http://www.opencaching.com/xmlschemas/opencaching/1/0";
    
    private File lastCreatedFile;
    private String currentCacheCode;
    //private double currentLatitude;
    //private double currentLongitude;
    private String gpxUrl;
    
    private HTMLProcessor htmlProcessor;
    
    // config
    private File sourceFile;
    private InputStream sourceStream;
    private File destFileBaseName;
    private long maxFileSize = 5*1024*1024;
    
    private List<GpxProcessMonitor> observers = new CopyOnWriteArrayList<GpxProcessMonitor>();
    
    private File tempDir;
    
    public void preCreateOutputStream()
    throws IOException
    {
        if (outputStream != null){
            return ;
        }
        createOutputStream(0);
    }
    
    @Override
    protected void finalize()
    throws Throwable
    {
        IOUtils.closeQuietly(outputStream);
        super.finalize();
    }
    
    protected void createOutputStream(int count) throws IOException
    {
        if (count == 0 && outputStream != null){
            return ;
        }
        final File fileName = getFileName(destFileBaseName, count);
        fileName.delete();
        OutputStream os = new FileOutputStream(fileName);
        // os = new BufferedOutputStream(os, 8192); 
        // nie ma potrzeby buforować, buforowaniem zajmuje się wewnętrznie serializer xml
        outputStream = new CountingOutputStream(os);
        lastCreatedFile = fileName;
    }

    protected File getFileName(File baseFileName, int count)
    {
        final File fileName;
        if (count <= 0){
            fileName = baseFileName;
        } else {
            String name = baseFileName.getName();
            int dot = name.lastIndexOf('.');
            if (dot == -1){
                name = name + '-' + count;
            } else {
                name = name.substring(0, dot) + '-' + count + name.substring(dot);
            }
            fileName = new File(baseFileName.getParentFile(), name);
        }
        return fileName;
    }
    
    boolean replaceAll(Matcher matcher, CharSequence source, char replacement, StringBuilder result) {
        matcher.reset();
        boolean result0 = matcher.find();
        int start = 0;
        if (result0) {
            do {
                int end = matcher.start();
                result.append(source.subSequence(start, end));
                result.append(replacement);
                start = matcher.end();
                result0 = matcher.find();
            } while (result0);
            result.append(source.subSequence(start, source.length()));
            return true;
        }
        return false;
    }
    
    protected int shouldProcessHtml(XmlPullParser parser) throws XmlPullParserException
    {
        if (parser.getEventType() != XmlPullParser.START_TAG){
            return 0;
        }
        
        if (!NS_GROUNDSPEAK.equals(parser.getNamespace())){
            return 0;
        }
        
        if ("long_description".equals(parser.getName())){
            String html = parser.getAttributeValue(null, "html");
            if (html != null && "true".equals(html.toLowerCase(Locale.US))){
                return ImageSourceResolver.SOURCE_MAIN;
            }
        }
        if ("text".equals(parser.getName())){
            return ImageSourceResolver.SOURCE_COMMENT;
        }
        return 0;
    }

    private XmlPullParser parser = null;
    private XmlSerializer serializer = null;

    boolean saveEventStream;
    private List<XMLEvent> gpxHeader;
    private CountingOutputStream outputStream;

    private void serializeText(String text) 
    throws IllegalArgumentException, IllegalStateException, IOException
    {
        if (saveEventStream){
            gpxHeader.add(XMLEvent.createText(text));
        }
        serializer.text(text);
    }

    private void setPrefix (String prefix, String namespace)
    throws IOException, IllegalArgumentException, IllegalStateException
    {
        if (saveEventStream){
            gpxHeader.add(XMLEvent.createNamespacePrefix(prefix, namespace));
        }
        serializer.setPrefix(prefix, namespace);
    }
    
    private void writeStartTag(XmlPullParser parser, XmlSerializer serializer)
    throws XmlPullParserException, IOException 
    {
        if (!parser.getFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES)) {
            int start = parser.getNamespaceCount(parser.getDepth() - 1);
            int end = parser.getNamespaceCount(parser.getDepth()) - 1;
            for (int i = start; i < end; i++) 
            {
                serializer.setPrefix(parser.getNamespacePrefix(i), parser.getNamespaceUri(i));
            }
        }
        serializer.startTag(parser.getNamespace(), parser.getName());

        for (int i = 0; i < parser.getAttributeCount(); i++) {
            serializer.attribute(parser.getAttributeNamespace(i), 
                parser.getAttributeName(i),
                parser.getAttributeValue(i));
        }
        
    }

    private void writeCurrentEvent()
    throws XmlPullParserException, IOException
    {
        if (saveEventStream){
            gpxHeader.add(XMLEvent.createFromParserState(parser));
        }
        switch (parser.getEventType()) {
            case XmlPullParser.START_DOCUMENT :
                serializer.startDocument (null, null);
                break;

            case XmlPullParser.END_DOCUMENT :
                serializer.endDocument();
                break;

            case XmlPullParser.START_TAG :
                writeStartTag(parser, serializer);
                break;

            case XmlPullParser.END_TAG :
                serializer.endTag(parser.getNamespace(), parser.getName());
                break;

            case XmlPullParser.IGNORABLE_WHITESPACE :
                break;

            case XmlPullParser.TEXT :
                if(parser.getText() == null){
                    logger.error("null text error at: " + parser.getPositionDescription());
                } else {
                    final String text = XMLEvent.trimText(parser.getText());
                    serializer.text(text);
                }
                break;

            case XmlPullParser.ENTITY_REF :
                if(parser.getText() != null){
                    serializer.text(parser.getText());
                } else { 
                    serializer.entityRef(parser.getName());
                }
                break;

            case XmlPullParser.CDSECT :
                serializer.cdsect(parser.getText());
                break;

            case XmlPullParser.PROCESSING_INSTRUCTION :
                serializer.processingInstruction(parser.getText());
                break;

            case XmlPullParser.COMMENT :
                serializer.comment(parser.getText());
                break;

            case XmlPullParser.DOCDECL :
                serializer.docdecl(parser.getText());
                break;

            default :
                throw new IllegalStateException("unrecognized event: " + parser.getEventType() + 
                    ", " + parser.getPositionDescription());
        }
    }
    
    @SuppressLint("SimpleDateFormat")
    public void processGpx()
    throws Exception
    {
        logger.info("START");

        InputStream is = null;
        int fileCount = 0;
        
        try{
            final long time0 = System.nanoTime(); 
            if (sourceStream != null){
                is = sourceStream;
            } else {
                is = new FileInputStream(sourceFile); 
                is = new BufferedInputStream(is, 16384);
            }

            createOutputStream(0);
            
            final XmlPullParserFactory xppf = XmlPullParserFactory.newInstance();
            
            parser = xppf.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

            serializer = xppf.newSerializer();
            serializer.setOutput(outputStream, "UTF-8");
            saveEventStream = true;
            
            String nsGpx = "";
            boolean hasAnyWpt = false;
            gpxHeader = new ArrayList<XMLEvent>(40);
            
            int wptDepth = 0;
            int imageSourceCode = 0;
            
            
            //currentLatitude = currentLongitude = Double.NaN;
            
            StringBuilder textBuffer1 = new StringBuilder(4096);
            StringBuilder textBuffer2 = new StringBuilder(4096);
            
            parser.setInput(is, null); // TODO: get charset from HTTP headers
            fileCount = 1;
            
            int startTagCount = 0;
            long startTagSumTime = 0;
            long startTagMaxTime = Long.MIN_VALUE;
            long startTagMinTime = Long.MAX_VALUE;
            long lastStartTagTime = 0;
            
            int startCacheCount = 0;
            long startCacheSumTime = 0;
            long startCacheMaxTime = Long.MIN_VALUE;
            long startCacheMinTime = Long.MAX_VALUE;
            long lastStartCacheTime = 0;
            
            final long time1 = System.nanoTime();
            int eventType;
            do{
                if (Thread.interrupted()){
                    throw new InterruptedException();
                }
                
                eventType = parser.getEventType();
                if (eventType == XmlPullParser.START_TAG){
                    {
                        long newNanoTime = System.nanoTime();
                        if (lastStartTagTime > 0){
                            long diff = newNanoTime-lastStartTagTime;
                            startTagSumTime = startTagSumTime + diff;
                            if (diff > startTagMaxTime){
                                startTagMaxTime = diff;
                            }
                            if (diff < startTagMinTime){
                                startTagMinTime = diff;
                            }
                            startTagCount++;
                        }
                        lastStartTagTime = newNanoTime;
                    }
                    
                    final String localName = parser.getName(); 
                    final int parserDepth = parser.getDepth();
                    if ("wpt".equals(localName)){
                        {
                            long newNanoTime = System.nanoTime();
                            if (lastStartCacheTime > 0){
                                long diff = newNanoTime-lastStartCacheTime;
                                startCacheSumTime = startCacheSumTime + diff;
                                if (diff > startCacheMaxTime){
                                    startCacheMaxTime = diff;
                                }
                                if (diff < startCacheMinTime){
                                    startCacheMinTime = diff;
                                }
                                startCacheCount++;
                            }
                            lastStartCacheTime = newNanoTime;
                        }
                        
                        if (saveEventStream){
                            XMLEvent lastEvent = gpxHeader.get(gpxHeader.size()-1);
                            if (lastEvent.getEventType() == XmlPullParser.TEXT && 
                                    XMLEvent.trimText(lastEvent.getText()).trim().length() == 0
                                || lastEvent.getEventType() == XmlPullParser.IGNORABLE_WHITESPACE)
                            {
                                gpxHeader.remove(gpxHeader.size()-1);
                            }
                                
                            saveEventStream = false;
                        }
                        
                        /*try{
                            currentLatitude = Double.parseDouble(parser.getAttributeValue(null, "lat"));
                            currentLongitude = Double.parseDouble(parser.getAttributeValue(null, "lon"));
                        }catch(NumberFormatException nfe){
                            currentLatitude = currentLongitude = Double.NaN;
                        }catch(NullPointerException npe){
                            currentLatitude = currentLongitude = Double.NaN;
                        }*/
                        hasAnyWpt = true;
                        wptDepth = parserDepth;
                    } else 
                    if ("gpx".equals(localName)){
                        nsGpx = parser.getNamespace();
                        
                        setPrefix("groundspeak", NS_GROUNDSPEAK);
                        setPrefix("ox", NS_OPENCACHING);
                    } else 
                    if (wptDepth == parserDepth-1 && "name".equals(localName))
                    {
                        writeCurrentEvent();
                        currentCacheCode = parser.nextText();
                        serializeText(currentCacheCode);
                                                
                        for (GpxProcessMonitor gpm : observers){
                            try{
                                gpm.onStartedCacheCode(currentCacheCode/*, currentLatitude, currentLongitude*/);
                            }catch(Exception e){
                                
                            }
                        }
                        continue;
                    } else 
                    if (parserDepth == 2 && gpxUrl == null && "url".equals(localName))
                    {
                        writeCurrentEvent();
                        gpxUrl = parser.nextText();
                        serializeText(gpxUrl);
                        continue;
                    }
                    
                    imageSourceCode = shouldProcessHtml(parser);
                    if (imageSourceCode > 0){
                        writeCurrentEvent();
                        textBuffer1.setLength(0);
                        textBuffer2.setLength(0);
                        final String htmlData = parser.nextText();
                        
                        htmlProcessor.processHtml(htmlData.trim(), textBuffer1, imageSourceCode);

                        if (replaceAll(whitespacePattern1.matcher(textBuffer1), 
                            textBuffer1, ' ', textBuffer2))
                        {
                            final StringBuilder temp = textBuffer1;
                            textBuffer1 = textBuffer2;
                            textBuffer2 = temp;
                            textBuffer2.setLength(0);
                        }
                        
                        if (replaceAll(whitespacePattern2.matcher(textBuffer1), 
                            textBuffer1, '\n', textBuffer2))
                        {
                            final StringBuilder temp = textBuffer1;
                            textBuffer1 = textBuffer2;
                            textBuffer2 = temp;
                            textBuffer2.setLength(0);
                        }
                        
                        serializeText(textBuffer1.toString());
                        
                        continue;
                    }
                }
                
                writeCurrentEvent();
                if (eventType == XmlPullParser.START_DOCUMENT){
                    serializeText("\n");
                    setPrefix("xsi", "http://www.w3.org/2001/XMLSchema-instance");
                }
                if (eventType == XmlPullParser.END_TAG){
                    final String localName = parser.getName(); 
                    if ("wpt".equals(localName)){
                        for (GpxProcessMonitor gpm : observers){
                            try{
                                gpm.onEndedCacheCode(currentCacheCode);
                            }catch(Exception e){
                                
                            }
                        }
                        currentCacheCode = null;
                        //currentLatitude = currentLongitude = Double.NaN;
                        wptDepth = -1;
                        
                        if (maxFileSize != 0 && outputStream.getByteCount() > maxFileSize){
                            serializer.text("\n");
                            serializer.endTag(nsGpx, "gpx");
                            serializer.text("\n");
                            serializer.endDocument();
                            
                            outputStream.flush();
                            outputStream.close();
                            outputStream = null;

                            if (fileCount == 1){
                                final File dest = getFileName(destFileBaseName, 1);
                                dest.delete();
                                destFileBaseName.renameTo(dest);
                                for (GpxProcessMonitor gpm : observers){
                                    try{
                                        gpm.onNewFile(fileCount, dest);
                                    }catch(Exception e){
                                        
                                    }
                                }
                            }
                            
                            // initalize new gpx part
                            fileCount++;
                            hasAnyWpt = false;
                            
                            createOutputStream(fileCount);
                            serializer.setOutput(outputStream, "UTF-8");

                            for (GpxProcessMonitor gpm : observers){
                                try{
                                    gpm.onNewFile(fileCount, lastCreatedFile);
                                }catch(Exception e){
                                    
                                }
                            }
                            
                            for (XMLEvent event2 : gpxHeader){
                                event2.pushToSerializer(serializer);
                            }

                            // delete potential following file
                            getFileName(destFileBaseName, fileCount+1).delete();
                        }
                    } 
                }
                eventType = parser.nextToken();
            }while(eventType != XmlPullParser.END_DOCUMENT);
            writeCurrentEvent();
            final long time3 = System.nanoTime();
            
            currentCacheCode = null;
            
            outputStream.flush();
            outputStream.close();
            outputStream = null;
            
            if (fileCount > 1 && !hasAnyWpt){
                lastCreatedFile.delete();
            }
            
            final long time4 = System.nanoTime();
            try{
                String s1 = "Total start tags=" + startTagCount + ", time=" + startTagSumTime +
                        ", min=" + startTagMinTime + ", max=" + startTagMaxTime + 
                        ", avg=" + Math.round((double)startTagSumTime / (double)startTagCount);
                String s2 = "Total caches=" + startCacheCount + ", time=" + startCacheSumTime +
                        ", min=" + startCacheMinTime + ", max=" + startCacheMaxTime + 
                        ", avg=" + Math.round((double)startCacheSumTime / (double)startCacheCount);
                String s3 = "Total processing time=" + (time4-time0) + 
                        ", preparation time=" + (time1-time0) + 
                        ", processing time=" + (time3-time1) +
                        ", finalization time=" + (time4-time3);
                if (logger.isDebugEnabled() || BuildConfig.DEBUG){
                    logger.debug(s1);
                    logger.debug(s2);
                    logger.debug(s3);
                }
                if (tempDir != null){
                    OutputStream os = null;
                    try{
                        os = new FileOutputStream(new File(tempDir, "GpxProcessor_timing.log"), true);
                        os = new BufferedOutputStream(os, 1024);
                        PrintWriter w = new PrintWriter(new OutputStreamWriter(os));
                        w.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                        w.print("thread id: ");
                        w.println(Thread.currentThread().getId());
                        w.println(s1);
                        w.println(s2);
                        w.println(s3);
                        w.println();
                        w.flush();
                    }finally{
                        IOUtils.closeQuietly(os);
                    }
                    
                }
            }catch(Exception e){
                
            }
        }catch(Exception e){
            logger.error("Failed processing cacheCode=" + currentCacheCode + ", filePart=" + fileCount);
            if (parser != null){
                try{
                    logger.error("Current event: " + XmlPullParser.TYPES[parser.getEventType()] + 
                        ", location=" + parser.getPositionDescription());
                    
                }catch(Exception e2){
                }
            }
            try{
                // for debug purposes
                if (serializer != null){
                    serializer.flush();
                }
                if (outputStream != null){
                    outputStream.flush();
                }
            }catch(Exception e2){
            }
            throw e;
        }finally{
            IOUtils.closeQuietly(outputStream);
            IOUtils.closeQuietly(is);
            gpxHeader = null;
            parser = null;
            serializer = null;
        }
        logger.info("END");
    }

    @Override
    public String getCurrentCacheCode()
    {
        return currentCacheCode;
    }

    @Override
    public String getGpxUrl()
    {
        return gpxUrl;
    }

    public File getSourceFile()
    {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile)
    {
        this.sourceFile = sourceFile;
        this.sourceStream = null;
    }

    public InputStream getSourceStream()
    {
        return sourceStream;
    }

    public void setSourceStream(InputStream sourceStream)
    {
        this.sourceStream = sourceStream;
        this.sourceFile = null;
    }

    public File getDestFileBaseName()
    {
        return destFileBaseName;
    }

    public void setDestFileBaseName(File destFileBaseName)
    {
        this.destFileBaseName = destFileBaseName;
    }

    public long getMaxFileSize()
    {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize)
    {
        if (maxFileSize != 0 && maxFileSize <= 10*1024){
            this.maxFileSize = 10*1024;
        } else {
            this.maxFileSize = maxFileSize;
        }
    }
    public HTMLProcessor getHtmlProcessor()
    {
        return htmlProcessor;
    }
    public void setHtmlProcessor(HTMLProcessor htmlProcessor)
    {
        this.htmlProcessor = htmlProcessor;
    }

    @Override
    public void close() throws IOException
    {
        IOUtils.closeQuietly(outputStream);
        IOUtils.closeQuietly(sourceStream);
    }

    public void addObserver(GpxProcessMonitor gpm)
    {
        observers.add(gpm);
    }

    public void removeObserver(GpxProcessMonitor gpm)
    {
        observers.remove(gpm);
    }

    public File getTempDir()
    {
        return tempDir;
    }

    public void setTempDir(File tempDir)
    {
        this.tempDir = tempDir;
    }

    /*@Override
    public double getCurrentLatitude()
    {
        return currentLatitude;
    }

    @Override
    public double getCurrentLongitude()
    {
        return currentLongitude;
    }*/
}
