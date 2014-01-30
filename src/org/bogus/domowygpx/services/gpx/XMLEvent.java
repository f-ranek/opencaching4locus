package org.bogus.domowygpx.services.gpx;

import java.io.IOException;

import org.bogus.ToStringBuilder;
import org.bogus.geocaching.egpx.BuildConfig;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class XMLEvent {
    public final static int NAMESPACE_PREFIX = 101;
    
    public final static String[] TYPES = {"NAMESPACE_PREFIX"}; 
    
    private int eventType;
    private String text;
    private String name;
    private String prefix;
    private String namespace;
    
    private String[] namespaceEvents;
    private String[] attributeEvents;
    
    public void pushToSerializer(XmlSerializer serializer) 
    throws IllegalArgumentException, IllegalStateException, IOException
    {
        switch (eventType) {
            case XmlPullParser.START_DOCUMENT :
                serializer.startDocument (null, null);
                break;

            case XmlPullParser.END_DOCUMENT :
                serializer.endDocument();
                break;

            case XmlPullParser.START_TAG :
                if (namespaceEvents != null){
                    for (int i=0; i<namespaceEvents.length; ){
                        String prefix = namespaceEvents[i++];
                        String namespace = namespaceEvents[i++];
                        serializer.setPrefix(prefix, namespace);
                    }
                }
                serializer.startTag(namespace, name);
                if (attributeEvents != null){
                    for (int i=0; i<attributeEvents.length; ){
                        String namespace = attributeEvents[i++];
                        String name = attributeEvents[i++];
                        String value = attributeEvents[i++];
                        serializer.attribute(namespace, name, value);
                    }
                }
                break;

            case XmlPullParser.END_TAG :
                serializer.endTag(namespace, name);
                break;

            case XmlPullParser.IGNORABLE_WHITESPACE :
                break;

            case XmlPullParser.TEXT :
                serializer.text(trimText(text));
                break;

            case XmlPullParser.ENTITY_REF :
                if(text != null){
                    serializer.text(text);
                } else { 
                    serializer.entityRef(name);
                }
                break;

            case XmlPullParser.CDSECT :
                serializer.cdsect(text);
                break;

            case XmlPullParser.PROCESSING_INSTRUCTION :
                serializer.processingInstruction(text);
                break;

            case XmlPullParser.COMMENT :
                serializer.comment(text);
                break;

            case XmlPullParser.DOCDECL :
                serializer.docdecl(text);
                break;

            case NAMESPACE_PREFIX :
                serializer.setPrefix(prefix, namespace);
                break;

            default :
                throw new IllegalStateException("unrecognized event: " + eventType);
        }
        
    }
    
    public static XMLEvent createText(String text)
    {
        XMLEvent result = new XMLEvent();
        result.eventType = XmlPullParser.TEXT;
        result.text = text;
        return result;
    }
    
    public static XMLEvent createNamespacePrefix(String prefix, String namespace)
    {
        XMLEvent result = new XMLEvent();
        result.eventType = XMLEvent.NAMESPACE_PREFIX;
        result.namespace = namespace;
        result.prefix = prefix;
        return result;
    }

    public static XMLEvent createFromParserState(XmlPullParser parser) 
    throws XmlPullParserException
    {
        XMLEvent result = new XMLEvent();
        result.eventType = parser.getEventType();
        switch(result.eventType){
            case XmlPullParser.START_DOCUMENT :
            case XmlPullParser.END_DOCUMENT :
                break;

            case XmlPullParser.START_TAG :
                if (!parser.getFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES)) {
                    int start = parser.getNamespaceCount(parser.getDepth() - 1);
                    int end = parser.getNamespaceCount(parser.getDepth()) - 1;
                    int len = end-start;
                    if (len > 0){
                        result.namespaceEvents = new String[len*2];
                    }
                    for (int i = start, j=0; i < end; i++) 
                    {
                        result.namespaceEvents[j++] = parser.getNamespacePrefix(i);
                        result.namespaceEvents[j++] = parser.getNamespaceUri(i);
                    }
                }
                result.namespace = parser.getNamespace();
                result.name = parser.getName();

                int count = parser.getAttributeCount();
                if (count > 0){
                    result.attributeEvents = new String[count*3];
                }
                for (int i = 0, j=0; i < count; i++) {
                    result.attributeEvents[j++] = parser.getAttributeNamespace(i); 
                    result.attributeEvents[j++] = parser.getAttributeName(i);
                    result.attributeEvents[j++] = parser.getAttributeValue(i);
                }
                break;

            case XmlPullParser.END_TAG :
                result.namespace = parser.getNamespace();
                result.name = parser.getName();
                break;

            case XmlPullParser.IGNORABLE_WHITESPACE :
                break;

            case XmlPullParser.ENTITY_REF :
                result.text = parser.getText();
                if (result.text == null){
                    result.name = parser.getName();
                }
                break;

            case XmlPullParser.TEXT :
            case XmlPullParser.CDSECT :
            case XmlPullParser.PROCESSING_INSTRUCTION :
            case XmlPullParser.COMMENT :
            case XmlPullParser.DOCDECL :
                result.text = parser.getText();
                break;
            default :
                throw new IllegalStateException("unrecognized event: " + parser.getEventType() + 
                    ", " + parser.getPositionDescription());
            
        }
        return result;
    }

    
    @Override
    public String toString()
    {
        if (BuildConfig.DEBUG){
            ToStringBuilder builder = new ToStringBuilder(this);
            if (eventType >= XMLEvent.NAMESPACE_PREFIX){
                builder.add("eventType", XMLEvent.TYPES[eventType-XMLEvent.NAMESPACE_PREFIX]);
            } else {
                builder.add("eventType", XmlPullParser.TYPES[eventType]);
            }
            builder.add("eventType", eventType);
            builder.add("text", text);
            builder.add("name", name);
            builder.add("prefix", prefix);
            builder.add("namespace", namespace);
            builder.add("namespaceEvents", namespaceEvents);
            builder.add("attributeEvents", attributeEvents);
            return builder.toString();
        } else {
            return super.toString();
        }
    }
    
    public static String trimText(String text)
    {
        if (text == null || text.length() == 0){
            return text;
        }
        boolean isEmpty = true;
        boolean hasNewLine = false;
        final int len = text.length();
        for (int i=0; i<len; i++){
            final char c = text.charAt(i);
            if (c == '\n' || c == '\r'){
                hasNewLine = true;
            } else 
            if (c != '\t' && c != ' '){
                isEmpty = false;
                break;
            }
        }
        if (isEmpty){
            return hasNewLine ? "\n" : "";
        } else {
            return text.trim();
        }
        
    }

    public int getEventType()
    {
        return eventType;
    }

    public String getText()
    {
        return text;
    }

    public String getName()
    {
        return name;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public String getNamespace()
    {
        return namespace;
    }
}