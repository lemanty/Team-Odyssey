package com.odyssey.retailshop.service;

import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;

/** Decode and re-encode uploads; never serve original file names or arbitrary uploaded bytes. */
public record SupportImage(byte[] bytes, String type) {
    public static SupportImage from(MultipartFile file) {
        if(file==null || file.isEmpty()) return null;
        if(file.getSize()>5*1024*1024) throw invalid();
        try(var raw=file.getInputStream(); var input=ImageIO.createImageInputStream(raw)) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw invalid();
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!format.equals("jpeg") && !format.equals("png")) throw invalid();
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>16000000) throw invalid();
                var image=reader.read(0);
                var output=new ByteArrayOutputStream();
                if(!ImageIO.write(image,format,output) || output.size()>8*1024*1024) throw invalid();
                return new SupportImage(output.toByteArray(),format.equals("png")?"image/png":"image/jpeg");
            } finally { reader.dispose(); }
        } catch(IOException e) { throw invalid(); }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Choose a JPG or PNG image up to 5 MB and 16 megapixels. / 请选择不超过5MB、1600万像素的JPG或PNG图片。");
    }
}
