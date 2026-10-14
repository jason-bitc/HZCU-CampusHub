package cn.edu.hzcu.campushub.authoring;

import cn.edu.hzcu.campushub.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class MediaStoreTest {
    @TempDir Path root;
    static byte[] png(int width,int height)throws Exception{var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();}
    @Test void decodedContentDimensionsFilenameAndDeclaredTypeAllChecked()throws Exception{
        var store=new MediaStore(root.toString(),1024,25);byte[] good=png(4,4);
        assertThat(store.validate(new MockMultipartFile("file","图.png","image/png",good)).width()).isEqualTo(4);
        var jpegOutput=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"jpeg",jpegOutput);byte[] jpeg=jpegOutput.toByteArray();
        var validJpeg=store.validate(new MockMultipartFile("file","图.jpeg","image/jpeg",jpeg));assertThat(validJpeg.mime()).isEqualTo("image/jpeg");assertThat(validJpeg.height()).isEqualTo(4);
        assertThatThrownBy(()->store.validate(new MockMultipartFile("file","wrong.png","image/png",jpeg))).isInstanceOf(ApiException.class);
        for(var file:List.of(new MockMultipartFile("file","photo.png","image/png","<svg/>".getBytes()),new MockMultipartFile("file","photo.jpg","image/jpeg",good),new MockMultipartFile("file","../../photo.png","image/png",good),new MockMultipartFile("file","photo.png","text/html",good),new MockMultipartFile("file","photo.png","image/png",Arrays.copyOf(good,good.length-10))))assertThatThrownBy(()->store.validate(file)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->store.validate(new MockMultipartFile("file","photo.png","image/png",png(6,5)))).isInstanceOf(ApiException.class);
    }
    @Test void fileByteLimitAndCorruptStoredFileAreExplicitFailures()throws Exception{
        byte[] bytes=png(4,4);var tiny=new MediaStore(root.resolve("tiny").toString(),bytes.length-1,100);
        assertThatThrownBy(()->tiny.validate(new MockMultipartFile("file","photo.png","image/png",bytes))).isInstanceOf(ApiException.class);
        var store=new MediaStore(root.resolve("normal").toString(),1024,100);String id=UUID.randomUUID().toString();store.install(id,bytes);
        assertThat(store.read(id,bytes.length,MediaStore.digest(bytes))).isEqualTo(bytes);
        Files.write(root.resolve("normal/objects").resolve(id),new byte[]{1});assertThatThrownBy(()->store.read(id,bytes.length,MediaStore.digest(bytes))).isInstanceOf(ApiException.class);
    }
}
