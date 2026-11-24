package com.campussync.app.util;

import android.graphics.Bitmap;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registration tickets.
 *
 * <p>A ticket payload is {@code campussync://event/{eventId}/registration/{uid}?token={token}}.
 * The token is a random secret written to
 * {@code events/{eventId}/registrations/{uid}.qrToken}; the organizer's scanner compares the
 * scanned token with that document before attendance is recorded, so a forged code cannot mark
 * anybody present.
 */
public final class QrCode {

    public static final String SCHEME = "campussync";
    public static final String HOST = "event";
    private static final String PATH_REGISTRATION = "registration";
    private static final String PARAM_TOKEN = "token";
    private static final String HEX = "0123456789abcdef";

    private QrCode() {
    }

    /** 32 hex characters of cryptographic randomness. */
    public static String newToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(32);
        for (byte value : bytes) {
            builder.append(HEX.charAt((value >> 4) & 0x0f));
            builder.append(HEX.charAt(value & 0x0f));
        }
        return builder.toString();
    }

    public static String buildPayload(String eventId, String uid, String token) {
        return SCHEME + "://" + HOST + "/" + eventId + "/" + PATH_REGISTRATION + "/" + uid
                + "?" + PARAM_TOKEN + "=" + token;
    }

    /** Parses a scanned code; returns {@code null} when it is not a CampusSync ticket. */
    public static Ticket parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        Uri uri;
        try {
            uri = Uri.parse(trimmed);
        } catch (Exception malformed) {
            return null;
        }
        if (!SCHEME.equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return null;
        java.util.List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 3) return null;
        if (!PATH_REGISTRATION.equals(segments.get(1))) return null;
        String token = uri.getQueryParameter(PARAM_TOKEN);
        if (token == null || token.isEmpty()) return null;
        return new Ticket(segments.get(0), segments.get(2), token);
    }

    /** Renders the payload as a square bitmap; returns {@code null} if encoding fails. */
    public static Bitmap toBitmap(String payload, int sizePx) {
        if (payload == null || payload.isEmpty() || sizePx <= 0) return null;
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE,
                    sizePx, sizePx, hints);
            int[] pixels = new int[sizePx * sizePx];
            for (int y = 0; y < sizePx; y++) {
                int offset = y * sizePx;
                for (int x = 0; x < sizePx; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx);
            return bitmap;
        } catch (WriterException | IllegalArgumentException error) {
            return null;
        }
    }

    /** Human readable form used as the ticket code under the QR image. */
    public static String shortCode(String token) {
        if (token == null || token.length() < 8) return token != null ? token : "";
        return (token.substring(0, 4) + "-" + token.substring(4, 8))
                .toUpperCase(Locale.getDefault());
    }

    /** Decoded ticket. */
    public static final class Ticket {
        public final String eventId;
        public final String uid;
        public final String token;

        Ticket(String eventId, String uid, String token) {
            this.eventId = eventId;
            this.uid = uid;
            this.token = token;
        }
    }
}
