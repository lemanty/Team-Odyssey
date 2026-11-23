package com.odyssey.retailshop.service;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.stream.ImageInputStream;

final class JpegOrientation {
    private JpegOrientation() {}

    static int read(ImageInputStream input) throws IOException {
        input.mark();
        try {
            if (input.readUnsignedShort() != 0xffd8) return 1;
            while (input.read() == 0xff) {
                int marker;
                do { marker = input.read(); } while (marker == 0xff);
                if (marker < 0 || marker == 0xda || marker == 0xd9) break;
                int length = input.readUnsignedShort() - 2;
                if (length < 0) break;
                if (marker == 0xe1) {
                    byte[] segment = new byte[length];
                    input.readFully(segment);
                    int value = fromExif(segment);
                    if (value != 0) return value;
                } else {
                    input.skipBytes(length);
                }
            }
            return 1;
        } finally {
            input.reset();
        }
    }

    private static int fromExif(byte[] segment) {
        ByteBuffer data = ByteBuffer.wrap(segment);
        if (segment.length < 16 || data.getInt(0) != 0x45786966 || data.getShort(4) != 0) return 0;
        int endian = data.getShort(6);
        if (endian == 0x4949) data.order(ByteOrder.LITTLE_ENDIAN);
        else if (endian != 0x4d4d) return 0;
        if (data.getShort(8) != 42) return 0;
        long directory = 6 + Integer.toUnsignedLong(data.getInt(10));
        if (directory < 14 || directory > segment.length - 2) return 0;
        int count = Short.toUnsignedInt(data.getShort((int) directory));
        int entry = (int) directory + 2;
        for (int i = 0; i < count && entry <= segment.length - 12; i++, entry += 12) {
            if (data.getShort(entry) == 0x112 && data.getShort(entry + 2) == 3 && data.getInt(entry + 4) == 1) {
                int value = data.getShort(entry + 8);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    static BufferedImage apply(BufferedImage source, int orientation) {
        int w = source.getWidth(), h = source.getHeight();
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1,0,0,1,w,0);
            case 3 -> new AffineTransform(-1,0,0,-1,w,h);
            case 4 -> new AffineTransform(1,0,0,-1,0,h);
            case 5 -> new AffineTransform(0,1,1,0,0,0);
            case 6 -> new AffineTransform(0,1,-1,0,h,0);
            case 7 -> new AffineTransform(0,-1,-1,0,h,w);
            case 8 -> new AffineTransform(0,-1,1,0,0,w);
            default -> null;
        };
        if (transform == null) return source;
        var result = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_RGB);
        var graphics = result.createGraphics();
        try { graphics.drawImage(source, transform, null); }
        finally { graphics.dispose(); }
        return result;
    }
}
