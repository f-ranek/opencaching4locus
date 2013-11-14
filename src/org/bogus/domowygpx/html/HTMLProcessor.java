package org.bogus.domowygpx.html;

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

    public void processHtml(String source, StringBuilder result, int sourcePlaceCode)
    {
        processHtml(new StringSource(source), source.length(), result, sourcePlaceCode);
    }
    
    public void processHtml(Source source, int estimatedSourceLengthMayBeZero, 
        StringBuilder result, int sourcePlaceCode)
    {
        final int resultLen = result.length();
        result.ensureCapacity(estimatedSourceLengthMayBeZero + result.length());
        final Page page = new Page(source);
        final Lexer lexer = new Lexer(page);
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
                                        attr.setValue(value2);
                                    }
                                }
                            }
                        }
                    }
                }
                node.toHtml(result);
            }
        }catch(ParserException pe){
            logger.error("Failed to process HTML, returning source data", pe);
            result.setLength(resultLen);
            result.append(source);
        }
    }
}
