package cn.nukkit.utils;

import cn.nukkit.nbt.stream.FastByteArrayOutputStream;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public abstract class Zlib {

    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);
    private static final ThreadLocal<Inflater> INFLATER_RAW = ThreadLocal.withInitial(() -> new Inflater(true));
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);
    private static final ThreadLocal<Deflater> DEFLATER_RAW = ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, true));
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

    public static byte[] deflateRaw(byte[] data, int level) throws IOException {
        Deflater deflater = DEFLATER_RAW.get();
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

    public static byte[] inflateRaw(byte[] data, int maxSize) throws IOException {
        Inflater inflater = INFLATER_RAW.get();
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
            throw new IOException("Unable to inflate raw Zlib stream", e);
        }
    }

    /**
     * Checks if data starts with a valid zlib header.
     * Zlib header: CMF byte (lower 4 bits = 8 for deflate) + FLG byte,
     * where (CMF * 256 + FLG) % 31 == 0.
     */
    public static boolean hasZlibHeader(byte[] data) {
        if (data.length < 2) return false;
        int cmf = data[0] & 0xff;
        int flg = data[1] & 0xff;
        return (cmf & 0x0F) == 8 && (cmf * 256 + flg) % 31 == 0;
    }

    /**
     * Auto-detects zlib vs raw deflate format and inflates accordingly.
     */
    public static byte[] inflateAuto(byte[] data, int maxSize) throws IOException {
        return hasZlibHeader(data) ? inflate(data, maxSize) : inflateRaw(data, maxSize);
    }
}
