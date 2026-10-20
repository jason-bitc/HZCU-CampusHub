package cn.edu.hzcu.campushub.authoring;

import cn.edu.hzcu.campushub.common.ApiException;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaStore {
    private static final Logger LOG=LoggerFactory.getLogger(MediaStore.class);
    private final Path root;
    private final int maxBytes;
    private final long maxPixels;
    public record Validated(byte[] bytes,String digest,String mime,int width,int height) {}
    public MediaStore(@Value("${campushub.media.root}") String directory,@Value("${campushub.media.max-bytes}") int maxBytes,@Value("${campushub.media.max-pixels}") long maxPixels){
        if(maxBytes<1||maxBytes>100*1024*1024||maxPixels<1||maxPixels>250_000_000)throw new IllegalArgumentException("Invalid media limits");
        this.maxBytes=maxBytes;this.maxPixels=maxPixels;root=Path.of(directory).toAbsolutePath().normalize();
        try{
            for(Path p=root;p!=null;p=p.getParent())if(Files.exists(p)&&(!p.toRealPath().equals(p)||Files.isSymbolicLink(p)))throw new IOException("Linked storage path");
            Files.createDirectories(root.resolve("objects"));Files.createDirectories(root.resolve("temporary"));
        }catch(IOException e){throw unavailable(e);}
    }
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(GeneralSecurityException e){throw new IllegalStateException("Digest unavailable");}}
    public Validated validate(MultipartFile file){
        cn.edu.hzcu.campushub.common.RequestTrace.stage(cn.edu.hzcu.campushub.common.RequestTrace.Stage.STORAGE);
        String name=file.getOriginalFilename();
        if(name==null||name.length()>255||name.contains("/")||name.contains("\\")||name.contains("..")||name.codePoints().anyMatch(Character::isISOControl))throw ApiException.invalid();
        String lower=name.toLowerCase(Locale.ROOT);boolean png=lower.endsWith(".png"),jpeg=lower.endsWith(".jpg")||lower.endsWith(".jpeg");
        if(!png&&!jpeg)throw ApiException.invalid();
        if(file.getSize()>maxBytes)throw tooLarge();
        byte[] bytes;
        try(var stream=file.getInputStream()){bytes=stream.readNBytes(maxBytes+1);}catch(IOException e){throw unavailable(e);}
        if(bytes.length>maxBytes)throw tooLarge();
        if(bytes.length==0)throw ApiException.invalid();
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw ApiException.invalid();var reader=readers.next();
            try{
                String format=reader.getFormatName();boolean actualPng=format.equalsIgnoreCase("png"),actualJpeg=format.equalsIgnoreCase("jpeg");
                if((!actualPng&&!actualJpeg)||png!=actualPng||jpeg!=actualJpeg)throw ApiException.invalid();
                String mime=actualPng?"image/png":"image/jpeg";
                String claimed=file.getContentType();if(claimed!=null&&!claimed.equals("application/octet-stream")&&!claimed.equals(mime))throw ApiException.invalid();
                if(actualPng){byte[] end={0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130};if(bytes.length<20||!Arrays.equals(Arrays.copyOfRange(bytes,bytes.length-12,bytes.length),end))throw ApiException.invalid();}
                else if(bytes.length<4||bytes[bytes.length-2]!=(byte)255||bytes[bytes.length-1]!=(byte)217)throw ApiException.invalid();
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0||(long)width*height>maxPixels)throw ApiException.invalid();
                if(reader.read(0)==null)throw ApiException.invalid();
                return new Validated(bytes,digest(bytes),mime,width,height);
            }finally{reader.dispose();}
        }catch(IOException e){throw ApiException.invalid();}
    }
    private Path checked(String directory,String name)throws IOException{
        Path parent=root.resolve(directory),path=parent.resolve(name).normalize();
        if(!path.startsWith(root)||!root.toRealPath().equals(root)||!parent.toRealPath().equals(parent)||Files.isSymbolicLink(path))throw new IOException("Unsafe media path");return path;
    }
    public void install(String id,byte[] bytes){
        cn.edu.hzcu.campushub.common.RequestTrace.stage(cn.edu.hzcu.campushub.common.RequestTrace.Stage.STORAGE);
        WritingInput.requestId(id);Path temp=null;
        try{
            Path destination=checked("objects",id);
            if(Files.exists(destination))throw new FileAlreadyExistsException("Generated image already exists");
            Path tempDir=checked("temporary","placeholder").getParent();temp=Files.createTempFile(tempDir,"upload-",".tmp");Files.write(temp,bytes);
            try{Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,destination);}
            temp=null;
        }catch(IOException e){throw unavailable(e);}
        finally{if(temp!=null)try{if(temp.toAbsolutePath().normalize().startsWith(root)&&!Files.isSymbolicLink(temp))Files.deleteIfExists(temp);}catch(IOException e){LOG.warn("own upload temporary cleanup failed type={}",e.getClass().getSimpleName());}}
    }
    public void rollbackCreated(String id){
        try{Files.deleteIfExists(checked("objects",WritingInput.requestId(id)));}
        catch(IOException e){LOG.warn("own rolled-back upload cleanup failed type={}",e.getClass().getSimpleName());}
    }
    public byte[] read(String id,long size,String digest){
        cn.edu.hzcu.campushub.common.RequestTrace.stage(cn.edu.hzcu.campushub.common.RequestTrace.Stage.STORAGE);
        try{
            Path path=checked("objects",WritingInput.requestId(id));if(Files.size(path)!=size||size>maxBytes)throw new IOException("Stored image mismatch");
            byte[] bytes=Files.readAllBytes(path);if(!MediaStore.digest(bytes).equals(digest))throw new IOException("Stored image mismatch");return bytes;
        }catch(IOException e){throw unavailable(e);}
    }
    public static ApiException tooLarge(){return new ApiException(HttpStatus.valueOf(413),"IMAGE_TOO_LARGE","图片超过允许大小");}
    private static ApiException unavailable(IOException e){LOG.warn("media storage failed type={}",e.getClass().getSimpleName());return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","图片存储暂时不可用，请稍后重试");}
}
