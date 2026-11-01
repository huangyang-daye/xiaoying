package com.xiaoying.studio;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileStorage {
    private final Path root;
    public FileStorage(@Value("${app.storage-root}") String directory) throws IOException {
        root=Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("uploads"));
        Files.createDirectories(root.resolve("results"));
        Files.createDirectories(root.resolve("samples"));
        Files.createDirectories(root.resolve("avatars"));
        var keys=List.of("postcard","collage","portrait","duo","pet","pet-story");
        for(int i=0;i<keys.size();i++) createSample(keys.get(i),i);
    }
    public void ensureSample(String key) throws IOException { createSample(key,Math.floorMod(key.hashCode(),6)); }
    private void createSample(String key,int index) throws IOException {
        Path file=path("samples/"+key+".png");
        if(Files.exists(file)) return;
        var image=new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        try {
            g.setColor(Color.getHSBColor(index/6f,.28f,.9f)); g.fillRect(0,0,800,600);
            g.setColor(new Color(255,255,255,150)); g.fillOval(390,30,350,350);
            g.setColor(new Color(35,60,55)); g.fillRoundRect(60,340,680,200,28,28);
            String label=key.length()>24?key.substring(0,24):key;
            g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,38)); g.drawString("STUDIO / "+label.toUpperCase(Locale.ROOT),90,420);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,22)); g.drawString("Local simulation sample",90,475);
        } finally { g.dispose(); }
        if(!ImageIO.write(image,"png",file.toFile())) throw new IOException("PNG encoder unavailable");
    }
    public Path path(String key) {
        Path resolved=root.resolve(key).normalize();
        if(!resolved.startsWith(root)) throw ApiException.invalid("非法文件路径");
        return resolved;
    }
    public String inspect(MultipartFile file,Models.Template template) {
        if(file.isEmpty()) throw ApiException.invalid("图片不能为空");
        if(file.getSize()>Math.min(template.maxFileSizeBytes,10485760)) throw new ApiException(413,"FILE_TOO_LARGE","图片超过模板大小限制");
        try(var input=ImageIO.createImageInputStream(file.getInputStream())) {
            if(input==null) throw ApiException.invalid("图片无法读取");
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new ApiException(400,"UNSUPPORTED_IMAGE_TYPE","仅支持有效的 JPEG 或 PNG 图片");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                String mime=switch(format) { case "jpeg","jpg" -> "image/jpeg"; case "png" -> "image/png"; default -> "unsupported"; };
                if(!Arrays.asList(template.acceptedMimeTypes.split(",")).contains(mime)) throw new ApiException(400,"UNSUPPORTED_IMAGE_TYPE","图片类型不符合模板要求");
                if((long)reader.getWidth(0)*reader.getHeight(0)>20000000) throw ApiException.invalid("图片像素不能超过 2000 万");
                if(reader.read(0)==null) throw ApiException.invalid("图片内容损坏");
                return mime;
            } finally { reader.dispose(); }
        } catch(IOException e) { throw ApiException.invalid("图片内容损坏或无法读取"); }
    }
    public String inspectAvatar(MultipartFile file) {
        if(file==null || file.isEmpty()) throw ApiException.invalid("请选择头像图片");
        if(file.getSize()>2*1024*1024) throw new ApiException(413,"FILE_TOO_LARGE","头像不能超过 2 MB");
        try(var input=ImageIO.createImageInputStream(file.getInputStream())) {
            if(input==null) throw ApiException.invalid("图片无法读取");
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new ApiException(400,"UNSUPPORTED_IMAGE_TYPE","头像仅支持有效的 JPEG 或 PNG 图片");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                String mime=switch(format) { case "jpeg","jpg" -> "image/jpeg"; case "png" -> "image/png"; default -> "unsupported"; };
                if(mime.equals("unsupported")) throw new ApiException(400,"UNSUPPORTED_IMAGE_TYPE","头像仅支持有效的 JPEG 或 PNG 图片");
                if((long)reader.getWidth(0)*reader.getHeight(0)>20000000) throw ApiException.invalid("图片像素不能超过 2000 万");
                if(reader.read(0)==null) throw ApiException.invalid("图片内容损坏");
                return mime;
            } finally { reader.dispose(); }
        } catch(IOException e) { throw ApiException.invalid("图片内容损坏或无法读取"); }
    }
    public void save(MultipartFile file,String key) {
        try(var in=file.getInputStream()) { Files.copy(in,path(key)); }
        catch(IOException e) { delete(key); throw new ApiException(503,"STORAGE_UNAVAILABLE","素材保存失败"); }
    }
    public String assemble(Models.Task task) throws IOException {
        String key="results/"+task.id+".png";
        Path temporary=Files.createTempFile(root.resolve("results"),task.id,".part");
        try {
            Files.copy(path("samples/"+task.sampleKey+".png"),temporary,StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary,path(key),StandardCopyOption.REPLACE_EXISTING);
            return key;
        } finally { Files.deleteIfExists(temporary); }
    }
    public void delete(String key) {
        try { Files.deleteIfExists(path(key)); } catch(IOException ignored) { /* Orphan cleanup can be done offline. */ }
    }
}
