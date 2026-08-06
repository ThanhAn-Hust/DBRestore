package com.dbbackup.security;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SegmentedCipherStreamTest {

    @Test
    void testEncryptAndDecryptRoundtripSingleSegment() throws Exception {
        byte[] originalData = "Hello World, Database Backup Test Payload!".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String passphrase = "SuperSecretPassword123";

        try (SegmentedCipherOutputStream cos = new SegmentedCipherOutputStream(baos, passphrase)) {
            cos.write(originalData);
        }

        byte[] encryptedBytes = baos.toByteArray();
        assertTrue(encryptedBytes.length > 32, "Header (32 bytes) + Payload must be written");

        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOs = new ByteArrayOutputStream();
        try (SegmentedCipherInputStream cis = new SegmentedCipherInputStream(bais, passphrase)) {
            cis.transferTo(decryptedOs);
        }

        assertArrayEquals(originalData, decryptedOs.toByteArray());
    }

    @Test
    void testEncryptAndDecryptMultiSegment() throws Exception {
        byte[] originalData = new byte[500];
        new Random(42).nextBytes(originalData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String passphrase = "MultiSegmentPassphrase!";
        int customSegmentSize = 64; // 64 bytes per segment -> ~8 segments

        try (SegmentedCipherOutputStream cos = new SegmentedCipherOutputStream(baos, passphrase, customSegmentSize)) {
            cos.write(originalData);
        }

        byte[] encryptedBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOs = new ByteArrayOutputStream();
        try (SegmentedCipherInputStream cis = new SegmentedCipherInputStream(bais, passphrase)) {
            cis.transferTo(decryptedOs);
        }

        assertArrayEquals(originalData, decryptedOs.toByteArray());
    }

    @Test
    void testInvalidPassphraseHandling() throws Exception {
        byte[] originalData = "Sensitive DB Backup Data".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String correctPassphrase = "RightPassword";
        String wrongPassphrase = "WrongPassword";

        try (SegmentedCipherOutputStream cos = new SegmentedCipherOutputStream(baos, correctPassphrase)) {
            cos.write(originalData);
        }

        byte[] encryptedBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOs = new ByteArrayOutputStream();

        assertThrows(Exception.class, () -> {
            try (SegmentedCipherInputStream cis = new SegmentedCipherInputStream(bais, wrongPassphrase)) {
                cis.transferTo(decryptedOs);
            }
        });
    }

    @Test
    void testCorruptedMagicByteHandling() throws Exception {
        byte[] originalData = "Data for magic byte test".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String passphrase = "TestPassword";

        try (SegmentedCipherOutputStream cos = new SegmentedCipherOutputStream(baos, passphrase)) {
            cos.write(originalData);
        }

        byte[] encryptedBytes = baos.toByteArray();
        // Corrupt magic byte (first byte)
        encryptedBytes[0] = (byte) 0xFF;

        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOs = new ByteArrayOutputStream();

        assertThrows(IOException.class, () -> {
            try (SegmentedCipherInputStream cis = new SegmentedCipherInputStream(bais, passphrase)) {
                cis.transferTo(decryptedOs);
            }
        });
    }

    @Test
    void testEmptyDataRoundtrip() throws Exception {
        byte[] originalData = new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String passphrase = "EmptyStreamPassphrase";

        try (SegmentedCipherOutputStream cos = new SegmentedCipherOutputStream(baos, passphrase)) {
            cos.write(originalData);
        }

        byte[] encryptedBytes = baos.toByteArray();
        // Should have header (32 bytes) + EOS marker (4 zero bytes) = 36 bytes total
        assertEquals(36, encryptedBytes.length);

        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOs = new ByteArrayOutputStream();
        try (SegmentedCipherInputStream cis = new SegmentedCipherInputStream(bais, passphrase)) {
            cis.transferTo(decryptedOs);
        }

        assertEquals(0, decryptedOs.toByteArray().length);
    }
}
