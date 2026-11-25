package com.campussync.app.util;

import java.util.Locale;

/**
 * Validation for uploaded academic resources.
 *
 * <p>Deliberately free of Android imports so the limits can be unit tested. The same limits are
 * enforced again by {@code storage.rules} (size and content type) and {@code firestore.rules}
 * (resource metadata), because a client-side check is a convenience, not a guarantee.
 */
public final class UploadValidator {

    /** 20 MB: lecture PDFs and slide decks fit, whole video lectures do not. */
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    public static final String[] ALLOWED_EXTENSIONS = {
            "pdf", "ppt", "pptx", "doc", "docx", "txt", "md", "png", "jpg", "jpeg"
    };

    /** Extensions the Storage rules accept (kept in step with {@link #ALLOWED_EXTENSIONS}). */
    public static final String ALLOWED_MIME_PATTERN =
            "application/pdf|"
                    + "application/vnd\\.ms-powerpoint|"
                    + "application/vnd\\.openxmlformats-officedocument\\.presentationml\\.presentation|"
                    + "application/msword|"
                    + "application/vnd\\.openxmlformats-officedocument\\.wordprocessingml\\.document|"
                    + "text/plain|text/markdown|text/x-markdown|"
                    + "image/png|image/jpeg";

    private UploadValidator() {
    }

    /** Lower-case extension without the dot, or "" when the name has none. */
    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedExtension(String fileName) {
        String extension = extensionOf(fileName);
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(extension)) return true;
        }
        return false;
    }

    /**
     * Human readable reason the file cannot be uploaded, or {@code null} when it can.
     *
     * @param fileName  the picker's display name
     * @param mimeType  the content type reported by the picker (may be generic)
     * @param sizeBytes the size reported by the picker, or a negative value when unknown
     */
    public static String validate(String fileName, String mimeType, long sizeBytes) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "Pick a file to upload";
        }
        if (!isAllowedExtension(fileName)) {
            return "That file type is not supported. Upload a PDF, slide deck, document or image.";
        }
        if (sizeBytes <= 0) {
            return "That file is empty or its size could not be read.";
        }
        if (sizeBytes > MAX_BYTES) {
            return "Files are limited to 20 MB. Split the document or compress it first.";
        }
        String mime = mimeType != null ? mimeType.trim().toLowerCase(Locale.ROOT) : "";
        if (!mime.isEmpty() && !"application/octet-stream".equals(mime)
                && !mime.matches(ALLOWED_MIME_PATTERN)) {
            return "That file's content type is not supported. Upload a PDF, slide deck, "
                    + "document or image.";
        }
        if (!mimeMatchesExtension(fileName, mime)) {
            return "That file's content does not match its name. Pick the original file again.";
        }
        return null;
    }

    /**
     * True when the reported content type belongs to the file extension. A picker that reports
     * no type (or a generic binary type) is accepted, because {@link #mimeFor} supplies the type
     * from the extension before the upload.
     */
    public static boolean mimeMatchesExtension(String fileName, String mimeType) {
        String mime = mimeType != null ? mimeType.trim().toLowerCase(Locale.ROOT) : "";
        if (mime.isEmpty() || "application/octet-stream".equals(mime)) return true;
        switch (extensionOf(fileName)) {
            case "pdf":
                return mime.equals("application/pdf");
            case "ppt":
                return mime.equals("application/vnd.ms-powerpoint");
            case "pptx":
                return mime.equals(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "doc":
                return mime.equals("application/msword");
            case "docx":
                return mime.equals(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "txt":
                return mime.equals("text/plain");
            case "md":
                return mime.equals("text/markdown") || mime.equals("text/x-markdown");
            case "png":
                return mime.equals("image/png");
            case "jpg":
            case "jpeg":
                return mime.equals("image/jpeg");
            default:
                return false;
        }
    }

    /** MIME type to store with the upload; derived from the name when the picker is vague. */
    public static String mimeFor(String fileName, String reportedMime) {
        String mime = reportedMime != null ? reportedMime.trim().toLowerCase(Locale.ROOT) : "";
        if (!mime.isEmpty() && !"application/octet-stream".equals(mime)) return mime;
        switch (extensionOf(fileName)) {
            case "pdf":
                return "application/pdf";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "md":
                return "text/markdown";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            default:
                return "text/plain";
        }
    }

    /** The resource type a file maps to when the uploader does not pick one explicitly. */
    public static String resourceTypeFor(String fileName) {
        switch (extensionOf(fileName)) {
            case "pdf":
                return "pdf";
            case "ppt":
            case "pptx":
                return "slides";
            default:
                return "notes";
        }
    }

    /**
     * A Storage-safe object name: ASCII letters, digits, dot, dash and underscore only. Uploads
     * keep their original name in Firestore so the download still looks familiar.
     */
    public static String safeFileName(String fileName) {
        if (fileName == null) return "resource";
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        if (cleaned.isEmpty()) cleaned = "resource";
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    /** "1.4 MB" / "480 KB" — used on the resource cards and detail screen. */
    public static String formatSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return Math.round(bytes / 1024.0) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
