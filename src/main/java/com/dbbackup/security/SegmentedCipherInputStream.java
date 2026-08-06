package com.dbbackup.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Input stream implementing Segmented AES-256-GCM decryption.
 * Reads header and length-prefixed encrypted segments.
 */
public class SegmentedCipherInputStream extends InputStream {

    private final InputStream in;
    private final SecretKey secretKey;
    private final byte[] baseIv;

    private byte[] currentSegmentBuffer = null;
    private int currentSegmentPos = 0;
    private int segmentIndex = 0;
    private boolean eosReached = false;

    public SegmentedCipherInputStream(InputStream in, String passphrase) throws IOException {
        this.in = in;

        // Read 32-byte header
        byte[] magic = new byte[4];
        readFully(in, magic);
        if (!Arrays.equals(magic, SegmentedCipherOutputStream.MAGIC_BYTES)) {
            throw new IOException("Invalid stream magic bytes - file is corrupt or not a db-backup encrypted file");
        }

        byte[] salt = new byte[SegmentedCipherOutputStream.SALT_LENGTH];
        readFully(in, salt);

        this.baseIv = new byte[SegmentedCipherOutputStream.BASE_IV_LENGTH];
        readFully(in, baseIv);

        this.secretKey = SegmentedCipherOutputStream.deriveKey(passphrase, salt);
    }

    @Override
    public int read() throws IOException {
        if (eosReached) {
            return -1;
        }
        if (currentSegmentBuffer == null || currentSegmentPos >= currentSegmentBuffer.length) {
            if (!fetchNextSegment()) {
                return -1;
            }
        }
        return currentSegmentBuffer[currentSegmentPos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (eosReached) {
            return -1;
        }
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        if (currentSegmentBuffer == null || currentSegmentPos >= currentSegmentBuffer.length) {
            if (!fetchNextSegment()) {
                return -1;
            }
        }

        int bytesAvailable = currentSegmentBuffer.length - currentSegmentPos;
        int bytesToCopy = Math.min(len, bytesAvailable);

        System.arraycopy(currentSegmentBuffer, currentSegmentPos, b, off, bytesToCopy);
        currentSegmentPos += bytesToCopy;

        return bytesToCopy;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private boolean fetchNextSegment() throws IOException {
        byte[] lengthBytes = new byte[4];
        int read = readOptional(in, lengthBytes);
        if (read < 4) {
            if (read == 0 && segmentIndex == 0) {
                // Empty file case
                eosReached = true;
                return false;
            }
            throw new EOFException("Premature EOF encountered; encrypted stream is missing mandatory End-Of-Stream marker (truncated file)");
        }

        int segmentLength = ByteBuffer.wrap(lengthBytes).getInt();
        if (segmentLength == 0) { // EOS marker
            eosReached = true;
            return false;
        }

        if (segmentLength < 0 || segmentLength > 512 * 1024 * 1024) { // 512MB max single segment limit safety
            throw new IOException("Corrupted segment length: " + segmentLength);
        }

        byte[] ciphertext = new byte[segmentLength];
        readFully(in, ciphertext);

        try {
            byte[] segmentIv = SegmentedCipherOutputStream.deriveSegmentIv(baseIv, segmentIndex);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(SegmentedCipherOutputStream.GCM_TAG_LENGTH_BITS, segmentIv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            this.currentSegmentBuffer = cipher.doFinal(ciphertext);
            this.currentSegmentPos = 0;
            this.segmentIndex++;
            return true;
        } catch (Exception e) {
            throw new IOException("Failed to decrypt stream segment " + segmentIndex + " (incorrect passphrase or corrupted data)", e);
        }
    }

    private static void readFully(InputStream is, byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            int count = is.read(buffer, bytesRead, buffer.length - bytesRead);
            if (count == -1) {
                throw new EOFException("Unexpected EOF while reading stream header/data");
            }
            bytesRead += count;
        }
    }

    private static int readOptional(InputStream is, byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            int count = is.read(buffer, bytesRead, buffer.length - bytesRead);
            if (count == -1) {
                return bytesRead;
            }
            bytesRead += count;
        }
        return bytesRead;
    }
}
