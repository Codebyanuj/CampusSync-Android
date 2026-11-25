package com.campussync.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The upload limits the UI relies on, checked without an Android device. */
public class UploadValidatorTest {

    @Test
    public void acceptsSupportedDocuments() {
        assertNull(UploadValidator.validate("trees.pdf", "application/pdf", 1024));
        assertNull(UploadValidator.validate("unit-3.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                2 * 1024 * 1024));
        assertNull(UploadValidator.validate("notes.txt", "text/plain", 512));
        assertNull(UploadValidator.validate("board.jpg", "image/jpeg", 900 * 1024));
    }

    @Test
    public void rejectsUnsupportedFiles() {
        assertNotNull(UploadValidator.validate("lecture.mp4", "video/mp4", 1024));
        assertNotNull(UploadValidator.validate("archive.zip", "application/zip", 1024));
        assertNotNull(UploadValidator.validate("script.exe", null, 1024));
        assertFalse(UploadValidator.isAllowedExtension("program"));
    }

    @Test
    public void rejectsEmptyAndOversizedFiles() {
        assertNotNull(UploadValidator.validate("trees.pdf", "application/pdf", 0));
        assertNotNull(UploadValidator.validate("trees.pdf", "application/pdf", -1));
        assertNotNull(UploadValidator.validate("trees.pdf", "application/pdf",
                UploadValidator.MAX_BYTES + 1));
        assertNull(UploadValidator.validate("trees.pdf", "application/pdf",
                UploadValidator.MAX_BYTES));
    }

    @Test
    public void rejectsContentTypesThatDoNotMatchTheFileKind() {
        assertNotNull(UploadValidator.validate("trees.pdf", "image/png", 1024));
        assertNull(UploadValidator.validate("trees.pdf", "application/octet-stream", 1024));
    }

    @Test
    public void derivesMimeTypesAndResourceTypes() {
        assertEquals("application/pdf", UploadValidator.mimeFor("trees.pdf", null));
        assertEquals("application/pdf",
                UploadValidator.mimeFor("trees.pdf", "application/octet-stream"));
        assertEquals("application/vnd.ms-powerpoint", UploadValidator.mimeFor("deck.ppt", null));
        assertEquals("text/markdown", UploadValidator.mimeFor("notes.md", null));
        assertEquals("pdf", UploadValidator.resourceTypeFor("trees.pdf"));
        assertEquals("slides", UploadValidator.resourceTypeFor("deck.pptx"));
        assertEquals("notes", UploadValidator.resourceTypeFor("notes.txt"));
    }

    @Test
    public void sanitisesObjectNames() {
        assertEquals("trees.pdf", UploadValidator.safeFileName("../trees.pdf"));
        assertEquals("notes_3.pdf", UploadValidator.safeFileName("unit 3/notes 3.pdf"));
        assertEquals("notes.pdf", UploadValidator.safeFileName("unit 3/notes.pdf"));
        assertEquals("trees.pdf", UploadValidator.safeFileName("...trees.pdf"));
        assertTrue(UploadValidator.safeFileName("../../").length() > 0);
    }

    @Test
    public void formatsFileSizes() {
        assertEquals("512 B", UploadValidator.formatSize(512));
        assertEquals("2 KB", UploadValidator.formatSize(2048));
        assertEquals("1.5 MB", UploadValidator.formatSize(1572864));
    }
}
