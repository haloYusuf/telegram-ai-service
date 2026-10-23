package com.techno.berijalan.telegramservice.util;

/**
 * Backward compatibility delegate for Image compression.
 * Main implementation is in MediaCompressionUtil.
 */
public class ImageCompressionUtil {

    public static byte[] compressAndResizeImage(byte[] originalBytes) {
        return MediaCompressionUtil.compressImage(originalBytes);
    }
}
