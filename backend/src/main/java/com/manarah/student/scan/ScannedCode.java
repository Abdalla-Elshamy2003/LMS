package com.manarah.student.scan;

/**
 * Normalizes whatever a reader or phone produced. A student's QR now encodes the public
 * verification URL ({@code https://host/student/verify/<token>}), so a keyboard-style card reader
 * types that whole URL; older QR codes ({@code /app/gate/<token>}) and bare tokens must keep working.
 */
public final class ScannedCode {
    private ScannedCode() {}

    /** Returns the bare code: trimmed, and for a URL the last path segment without query or fragment. */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.indexOf('/') < 0) return value;
        int end = value.length();
        for (char c : new char[] {'?', '#'}) {
            int at = value.indexOf(c);
            if (at >= 0 && at < end) end = at;
        }
        String path = value.substring(0, end);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
