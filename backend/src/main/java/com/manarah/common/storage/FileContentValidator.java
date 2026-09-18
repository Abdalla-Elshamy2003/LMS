package com.manarah.common.storage;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks that an uploaded file's actual bytes match what its extension claims, so a script or
 * executable renamed to "homework.pdf" doesn't sail through on the extension allow-list alone.
 * This is defense-in-depth, not a full file-format validator: extensions with no reliable simple
 * signature (mp3, m4a) are left unchecked rather than risk rejecting legitimate files on a
 * guess - the extension allow-list in FileController is still the primary gate for those.
 */
final class FileContentValidator {
    private FileContentValidator() {}

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04}; // docx/pptx/xlsx/zip are all ZIP containers
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}; // legacy .doc/.ppt
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF" - webp/wav container prefix
    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}; // webm

    private static final Map<String, byte[]> EXACT_PREFIX = Map.of(
            "pdf", PDF,
            "png", PNG,
            "jpg", JPEG,
            "jpeg", JPEG,
            "docx", ZIP,
            "pptx", ZIP,
            "zip", ZIP,
            "doc", OLE2,
            "ppt", OLE2,
            "webm", EBML
    );

    /** mp4/m4v/mov use an "ftyp" box that doesn't start at byte 0 - the first 4 bytes are a box size. */
    private static final int FTYP_OFFSET = 4;
    private static final byte[] FTYP = {0x66, 0x74, 0x79, 0x70}; // "ftyp"

    static void assertMatchesExtension(MultipartFile file, String extension) {
        byte[] header;
        try (var in = file.getInputStream()) {
            header = in.readNBytes(16);
        } catch (IOException e) {
            throw new BadRequestException("تعذّرت قراءة الملف");
        }
        String ext = extension.toLowerCase(Locale.ROOT);

        byte[] expected = EXACT_PREFIX.get(ext);
        if (expected != null) {
            if (!startsWith(header, expected)) throw mismatch();
            return;
        }
        if (Set.of("mp4", "m4v", "mov").contains(ext)) {
            if (header.length < FTYP_OFFSET + 4 || !startsWith(slice(header, FTYP_OFFSET), FTYP)) throw mismatch();
            return;
        }
        if ("webp".equals(ext)) {
            if (!startsWith(header, RIFF)) throw mismatch();
            return;
        }
        // mp3/wav/m4a and anything else not listed above: no reliable simple signature, skip.
    }

    private static BadRequestException mismatch() {
        return new BadRequestException("محتوى الملف لا يطابق امتداده");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static byte[] slice(byte[] data, int from) {
        byte[] out = new byte[data.length - from];
        System.arraycopy(data, from, out, 0, out.length);
        return out;
    }
}
