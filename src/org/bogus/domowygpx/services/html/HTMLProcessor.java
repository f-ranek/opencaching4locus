package org.bogus.domowygpx.services.html;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.bogus.logging.LogFactory;
import org.htmlparser.Attribute;
import org.htmlparser.Node;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.lexer.Source;
import org.htmlparser.lexer.StringSource;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.util.ParserException;

public class HTMLProcessor
{
    private final static Log logger = LogFactory.getLog(HTMLProcessor.class);

    private static final Pattern entitiesPattern1 = 
            Pattern.compile(
                "&(quot|amp|lt|gt|#([0-9]+)|#x([0-9A-F]+));", Pattern.CASE_INSENSITIVE);
    
    private ImageSourceResolver imageSourceResolver;

    public HTMLProcessor(ImageSourceResolver imageSourceResolver)
    {
        this.imageSourceResolver = imageSourceResolver;
    }

    /**
     * Processes source data into <var>result</var>. If there was no changes, <var>result</var>
     * is untouched, and method returns <code>false</code>. If there was any change, processed
     * HTML is appended to <var>result</var>, and method returns <code>true</code>. 
     * @param source
     * @param result
     * @param sourcePlaceCode Tag to pass to {@link ImageSourceResolver}
     * @return true, it there were any changes, false otherwise
     */
    public boolean processHtml(String source, StringBuilder result, int sourcePlaceCode)
    {
        return processHtml(new StringSource(source), source.length(), result, sourcePlaceCode);
    }
    
    /**
     * See {@link #processHtml(String, StringBuilder, int)}
     * @param source
     * @param estimatedSourceLengthMayBeZero
     * @param result
     * @param sourcePlaceCode
     * @return
     */
    public boolean processHtml(Source source, int estimatedSourceLengthMayBeZero, 
        StringBuilder result, int sourcePlaceCode)
    {
        final int resultLen = result.length();
        final Page page = new Page(source);
        final Lexer lexer = new Lexer(page);
        List<Node> nodes = new ArrayList<Node>();
        boolean hasAnyChange = false;
        try{
            for (Node node = lexer.nextNode(); node != null; node = lexer.nextNode()){
                if (node instanceof TagNode){
                    TagNode tag = (TagNode)node;
                    String tagName = tag.getRawTagName();
                    if (tagName != null && tagName.length() == 3 && 
                            tagName.toLowerCase(Locale.ENGLISH).equals("img"))
                    {
                        List<Attribute> attributes = tag.getAttributesEx();
                        if (attributes != null){
                            for (int i=0; i<attributes.size(); i++){
                                final Attribute attr = attributes.get(i);
                                final String name = attr.getName();
                                if (name != null && name.length() == 3 && 
                                        name.toLowerCase(Locale.ENGLISH).equals("src"))
                                {
                                    String value = attr.getValue();
                                    if (value != null && value.length() > 0){
                                        String value2 = unescapeHtml(value);
                                        value2 = imageSourceResolver.processImgSrc(value2, sourcePlaceCode);
                                        value2 = escapeHtml(value2);
                                        if (!value2.equals(value)){
                                            attr.setValue(value2);
                                            hasAnyChange = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (hasAnyChange && estimatedSourceLengthMayBeZero > 0 && estimatedSourceLengthMayBeZero < 8192){
                    // huge estimatedSourceLengthMayBeZero means, that we are dealing with an image 
                    // - don't grow buffer unnecessary
                    result.ensureCapacity(estimatedSourceLengthMayBeZero + result.length());
                    estimatedSourceLengthMayBeZero = 0;
                }

                if (hasAnyChange){
                    if (nodes != null){
                        for (Node savedNode : nodes){
                            savedNode.toHtml(result);
                        }
                        nodes = null;
                    }
                    node.toHtml(result);
                } else {
                    nodes.add(node);
                }
            }
            return hasAnyChange;
        }catch(ParserException pe){
            logger.error("Failed to process HTML, returning source data", pe);
            result.setLength(resultLen);
            return false; 
        }
    }
    
    protected String escapeHtml(String str)
    {
        StringBuilder result = null;
        int len = str.length();
        for (int i=0; i<len; i++){
            char c = str.charAt(i);
            if (c == '&' || c == '"' || c == '>' || c == '<'){
                if (result == null){
                    result = new StringBuilder(str.length() + 20);
                    result.append(str.substring(0, i));
                }
                result.append('&');
                switch(c){
                    case '&': result.append("amp"); break;
                    case '"': result.append("quot"); break;
                    case '>': result.append("gt"); break;
                    case '<': result.append("lt"); break;
                }
                result.append(';');
            } else {
                if (result != null){
                    result.append(c);
                }
            }
        }
        if (result != null){
            return result.toString();
        } else {
            return str;
        }
    }

    protected String unescapeHtml(String str)
    {
        // yes, i know there is org.apache.commons.lang3.StringEscapeUtils
        StringBuilder result = unescape2Html(str);
        if (result == null){
            return str;
        } else {
            return result.toString();
        }
    }
    
    protected StringBuilder unescape2Html(CharSequence source) {
        Matcher matcher = entitiesPattern1.matcher(source);
        boolean result0 = matcher.find();
        int start = 0;
        if (result0) {
            StringBuilder result = new StringBuilder(source.length());
            do {
                int end = matcher.start();
                result.append(source.subSequence(start, end));
                String entity = matcher.group(1).toLowerCase(Locale.US);
                try{
                    if ("amp".equals(entity)){
                        result.append('&');
                    } else 
                    if ("quot".equals(entity)){
                        result.append('"');
                    } else 
                    if ("lt".equals(entity)){
                        result.append('<');
                    } else 
                    if ("gt".equals(entity)){
                        result.append('>');
                    } else 
                    if (entity.startsWith("#x")){
                        String num = matcher.group(3); 
                        int x = Integer.parseInt(num, 16);
                        result.append((char)x);
                    } else
                    if (entity.startsWith("#")){
                        String num = matcher.group(2); 
                        int x = Integer.parseInt(num);
                        result.append((char)x);
                    } else {
                        // should not happen
                        result.append('&').append(entity).append(';');
                    }
                }catch(NumberFormatException nfe){
                    result.append('&').append(entity).append(';');
                }
                start = matcher.end();
                result0 = matcher.find();
            } while (result0);
            result.append(source.subSequence(start, source.length()));
            return result;
        }
        return null;
    }
}
