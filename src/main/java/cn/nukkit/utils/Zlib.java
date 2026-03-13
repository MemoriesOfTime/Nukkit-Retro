package cn.nukkit.utils;

import cn.nukkit.nbt.stream.FastByteArrayOutputStream;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public abstract class Zlib {

    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);
    private static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);
    private static final ThreadLocal<FastByteArrayOutputStream> FBAOS = ThreadLocal.withInitial(() -> new FastByteArrayOutputStream(1024));

    public static byte[] deflate(byte[] data) throws IOException {
        return deflate(data, Deflater.DEFAULT_COMPRESSION);
    }

    public static byte[] deflate(byte[] data, int level) throws IOException {
        Deflater deflater = DEFLATER.get();
        deflater.reset();
        deflater.setLevel(level);
        deflater.setInput(data);
        deflater.finish();
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        while (!deflater.finished()) {
            int i = deflater.deflate(buffer);
            bos.write(buffer, 0, i);
        }
        return bos.toByteArray();
    }

    public static byte[] deflate(byte[][] datas, int level) throws IOException {
        Deflater deflater = DEFLATER.get();
        deflater.reset();
        deflater.setLevel(level);
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        for (byte[] data : datas) {
            deflater.setInput(data);
            while (!deflater.needsInput()) {
                int i = deflater.deflate(buffer);
                bos.write(buffer, 0, i);
            }
        }
        deflater.finish();
        while (!deflater.finished()) {
            int i = deflater.deflate(buffer);
            bos.write(buffer, 0, i);
        }
        return bos.toByteArray();
    }

    public static byte[] inflate(byte[] data) throws IOException {
        return inflate(data, 0);
    }

    public static byte[] inflate(byte[] data, int maxSize) throws IOException {
        Inflater inflater = INFLATER.get();
        inflater.reset();
        inflater.setInput(data);
        inflater.finished();
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        try {
            int length = 0;
            while (!inflater.finished()) {
                int i = inflater.inflate(buffer);
                if (i == 0) {
                    throw new IOException("Could not decompress data");
                }
                length += i;
                if (maxSize > 0 && length >= maxSize) {
                    throw new IOException("Inflated data exceeds maximum size");
                }
                bos.write(buffer, 0, i);
            }
            return bos.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Unable to inflate Zlib stream", e);
        }
    }
}
