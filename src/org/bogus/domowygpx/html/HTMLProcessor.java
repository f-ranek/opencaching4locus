package org.bogus.domowygpx.html;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                                        String value2 = imageSourceResolver.processImgSrc(value, sourcePlaceCode);
                                        if (value2 != null){
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
}
