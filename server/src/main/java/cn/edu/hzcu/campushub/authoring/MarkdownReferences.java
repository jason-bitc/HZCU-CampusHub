package cn.edu.hzcu.campushub.authoring;

import cn.edu.hzcu.campushub.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarkdownReferences {
    private final int maxBytes;
    public MarkdownReferences(@Value("${campushub.authoring.body-max-bytes}") int maxBytes){if(maxBytes<1||maxBytes>16*1024*1024)throw new IllegalArgumentException("Invalid Markdown byte limit");this.maxBytes=maxBytes;}
    public record References(List<String> imageIds,String readableText) {}
    public References inspect(String markdown){
        if(markdown.getBytes(StandardCharsets.UTF_8).length>maxBytes)throw new ApiException(org.springframework.http.HttpStatus.valueOf(413),"CONTENT_TOO_LARGE","Markdown正文超过允许大小");
        var images=new TreeSet<String>();var text=new StringBuilder();
        Parser.builder().build().parse(markdown).accept(new AbstractVisitor(){
            @Override public void visit(Image image){
                String url=image.getDestination();
                if(url.startsWith("/api/v1/media/images/")){String id=url.substring("/api/v1/media/images/".length());images.add(WritingInput.requestId(id));}
                else WritingInput.httpUrl(url);
                // Image alt text is not considered readable article text and never interpreted as another reference.
            }
            @Override public void visit(Text node){text.append(node.getLiteral()).append(' ');}
            @Override public void visit(Code node){text.append(node.getLiteral()).append(' ');}
            @Override public void visit(FencedCodeBlock node){text.append(node.getLiteral()).append(' ');}
            @Override public void visit(IndentedCodeBlock node){text.append(node.getLiteral()).append(' ');}
        });
        return new References(List.copyOf(images),text.toString().strip());
    }
    public static String summary(String text){String value=text.replaceAll("\\s+"," ").strip();int end=value.offsetByCodePoints(0,Math.min(1000,value.codePointCount(0,value.length())));return value.substring(0,end);}
}
