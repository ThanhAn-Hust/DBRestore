package com.dbbackup.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * Output stream implementing Segmented AES-256-GCM encryption.
 * Writes a 32-byte header followed by length-prefixed encrypted segments.
 */
public class SegmentedCipherOutputStream extends FilterOutputStream {

    public static final byte[] MAGIC_BYTES = new byte[]{0x44, 0x42, 0x42, 0x4B}; // "DBBK"
    public static final int SALT_LENGTH = 16;
    public static final int BASE_IV_LENGTH = 12;
    public static final int HEADER_LENGTH = 32; // 4 + 16 + 12
    public static final int DEFAULT_SEGMENT_SIZE = 64 * 1024 * 1024; // 64 MB
    public static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final byte[] baseIv;
    private final int maxSegmentSize;
    private final byte[] buffer;
    private int bufferPos = 0;
    private int segmentIndex = 0;
    private boolean headerWritten = false;
    private boolean closed = false;

    public SegmentedCipherOutputStream(OutputStream out, String passphrase) throws IOException {
        this(out, passphrase, DEFAULT_SEGMENT_SIZE);
    }

    public SegmentedCipherOutputStream(OutputStream out, String passphrase, int segmentSize) throws IOException {
        super(out);
        if (segmentSize <= 0) {
            throw new IllegalArgumentException("Segment size must be positive");
        }
        this.maxSegmentSize = segmentSize;
        this.buffer = new byte[segmentSize];

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        this.baseIv = new byte[BASE_IV_LENGTH];
        random.nextBytes(baseIv);

        this.secretKey = deriveKey(passphrase, salt);

        // Write header
        out.write(MAGIC_BYTES);
        out.write(salt);
        out.write(baseIv);
        this.headerWritten = true;
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        buffer[bufferPos++] = (byte) b;
        if (bufferPos >= maxSegmentSize) {
            flushSegment();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }

        int bytesRemaining = len;
        int currentOff = off;

        while (bytesRemaining > 0) {
            int spaceAvailable = maxSegmentSize - bufferPos;
            int bytesToCopy = Math.min(bytesRemaining, spaceAvailable);

            System.arraycopy(b, currentOff, buffer, bufferPos, bytesToCopy);
            bufferPos += bytesToCopy;
            currentOff += bytesToCopy;
            bytesRemaining -= bytesToCopy;

            if (bufferPos >= maxSegmentSize) {
                flushSegment();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        // Do not flush segment prematurely unless needed, just flush underlying output stream
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            if (bufferPos > 0) {
                flushSegment();
            }
            // Write End-Of-Stream (EOS) marker (4 zero bytes length)
            byte[] eosMarker = new byte[4];
            out.write(eosMarker);
            out.flush();
        } finally {
            closed = true;
            super.close();
        }
    }

    private void flushSegment() throws IOException {
        if (bufferPos == 0) {
            return;
        }
        try {
            byte[] segmentIv = deriveSegmentIv(baseIv, segmentIndex);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, segmentIv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(buffer, 0, bufferPos);

            // Write 4-byte big-endian segment length N
            byte[] lengthBytes = ByteBuffer.allocate(4).putInt(ciphertext.length).array();
            out.write(lengthBytes);
            out.write(ciphertext);

            bufferPos = 0;
            segmentIndex++;
        } catch (Exception e) {
            throw new IOException("Failed to encrypt stream segment " + segmentIndex, e);
        }
    }

    public static SecretKey deriveKey(String passphrase, byte[] salt) throws IOException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            throw new IOException("Failed to derive secret key from passphrase", e);
        }
    }

    public static byte[] deriveSegmentIv(byte[] baseIv, int index) {
        byte[] segmentIv = Arrays.copyOf(baseIv, baseIv.length);
        byte[] indexBytes = ByteBuffer.allocate(4).putInt(index).array();
        for (int i = 0; i < 4; i++) {
            segmentIv[8 + i] ^= indexBytes[i];
        }
        return segmentIv;
    }
}
