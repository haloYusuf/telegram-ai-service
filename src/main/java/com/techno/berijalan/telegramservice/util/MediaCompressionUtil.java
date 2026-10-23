package com.techno.berijalan.telegramservice.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Utilitas Terpadu Kompresi Media Multimodal: Foto, Dokumen PDF, dan Video MP4.
 * Mengurangi ukuran berkas secara signifikan tanpa menurunkan informasi esensial:
 * 1. Foto: Kompresi resolusi maks 1280px dan JPEG 80% (teks dokumen KTP/STNK tetap tajam dan terbaca jelas).
 * 2. Dokumen PDF: Optimasi objek dan kompresi aliran FlateDecode via Apache PDFBox (100% Lossless).
 * 3. Video MP4: Optimasi struktur MP4 container (FastStart) dan eliminasi padding box kosong (100% Lossless).
 */
@Slf4j
public class MediaCompressionUtil {

    private static final int MAX_IMAGE_DIMENSION = 1280;
    private static final float JPEG_QUALITY = 0.80f;

    /**
     * Entry-point kompresi media adaptif berdasarkan jenis media: IMAGE, DOCUMENT, atau VIDEO.
     * Kebijakan Arsitektur Dokumen PDF:
     * - Inbound & Outbound: Disimpan dan ditransmisikan apa adanya (raw/uncompressed) untuk menjamin
     *   integritas hukum, verifikasi tanda tangan digital/audit trail, dan kepatuhan regulasi finansial.
     * - AI Pipeline (Gemini): Kompresi PDF (Embedded Image Downsampling) dilakukan secara on-the-fly
     *   di memory saat hendak dianalisis oleh AI di TelegramAiService.
     */
    public static byte[] compressMedia(byte[] originalBytes, String mediaType) {
        if (originalBytes == null || originalBytes.length == 0) {
            return originalBytes;
        }

        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            return compressImage(originalBytes);
        } else if ("DOCUMENT".equalsIgnoreCase(mediaType)) {
            // PDF inbound & outbound dipertahankan apa adanya (raw) sesuai kebijakan integritas berkas
            return originalBytes;
        } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return compressVideo(originalBytes);
        }

        return originalBytes;
    }

    /**
     * KOMPRESI FOTO / GAMBAR
     * Memperkecil resolusi foto kamera HP (maksimal 1280px) dan mengompresi ke JPEG 80%.
     * Teks pada foto dokumen (KTP, SIM, STNK, struk transfer) tetap 100% tajam dan terbaca.
     */
    public static byte[] compressImage(byte[] originalBytes) {
        if (originalBytes == null || originalBytes.length == 0) {
            return originalBytes;
        }

        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (originalImage == null) {
                log.warn("[MediaCompressionUtil] Could not decode image bytes, returning original bytes");
                return originalBytes;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            int targetWidth = originalWidth;
            int targetHeight = originalHeight;

            if (originalWidth > MAX_IMAGE_DIMENSION || originalHeight > MAX_IMAGE_DIMENSION) {
                if (originalWidth >= originalHeight) {
                    targetWidth = MAX_IMAGE_DIMENSION;
                    targetHeight = (int) Math.round((double) originalHeight * MAX_IMAGE_DIMENSION / originalWidth);
                } else {
                    targetHeight = MAX_IMAGE_DIMENSION;
                    targetWidth = (int) Math.round((double) originalWidth * MAX_IMAGE_DIMENSION / originalHeight);
                }
            }

            BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                ImageIO.write(resizedImage, "jpeg", baos);
                return baos.toByteArray();
            }

            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(resizedImage, null, null), param);
            } finally {
                writer.dispose();
            }

            byte[] compressedBytes = baos.toByteArray();
            log.info("[MediaCompressionUtil] Resized/compressed image from {} to {} bytes ({}x{} -> {}x{})",
                    originalBytes.length, compressedBytes.length, originalWidth, originalHeight, targetWidth, targetHeight);
            return compressedBytes;
        } catch (Exception e) {
            log.error("[MediaCompressionUtil] Failed to compress image: {}, returning original bytes", e.getMessage());
            return originalBytes;
        }
    }

    /**
     * KOMPRESI DOKUMEN PDF (100% Lossless)
     * Menggunakan Apache PDFBox untuk memadatkan stream dokumen, membersihkan objek duplikat/usang,
     * serta mengaktifkan kompresi FlateDecode standar industri tanpa menghapus sebaris pun teks atau tanda tangan.
     */
    public static byte[] compressPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return pdfBytes;
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Simpan dokumen dengan kompresi stream FlateDecode standar
            document.save(baos, CompressParameters.DEFAULT_COMPRESSION);
            byte[] compressed = baos.toByteArray();

            // Jika hasil kompresi lebih kecil, gunakan hasil kompresi
            if (compressed.length < pdfBytes.length) {
                log.info("[MediaCompressionUtil] Compressed PDF from {} to {} bytes (Saved {:.1f}%)",
                        pdfBytes.length, compressed.length,
                        ((pdfBytes.length - compressed.length) / (double) pdfBytes.length) * 100.0);
                return compressed;
            }
            return pdfBytes;
        } catch (Exception e) {
            log.warn("[MediaCompressionUtil] PDF optimization skipped (invalid or encrypted PDF): {}", e.getMessage());
            return pdfBytes;
        }
    }

    public static final int MAX_PDF_IMAGE_DIMENSION = 1280;
    public static final float PDF_JPEG_QUALITY = 0.80f;

    /**
     * KOMPRESI DOKUMEN PDF DENGAN SMART IMAGE DOWNSAMPLING & OPTIMASI (1280px / 80%)
     * - Teks dokumen, tabel, angka, dan klausul hukum tetap 100% utuh tanpa diubah.
     * - Preservasi Transparansi & Soft Mask: Aset dengan transparansi (cutout gedung, logo, grafik overlay)
     *   TIDAK diubah menjadi kotak putih/hitam, menjaga foto dan desain visual tetap utuh dan terlihat jelas.
     * - Resolusi Maksimal 1280px & Kualitas JPEG 80%: Sangat optimal untuk visual input multimodal AI Gemini.
     */
    public static byte[] compressPdfDownsample(byte[] pdfBytes) {
        return compressPdfDownsample(pdfBytes, MAX_PDF_IMAGE_DIMENSION, PDF_JPEG_QUALITY);
    }

    public static byte[] compressPdfDownsample(byte[] pdfBytes, int maxDimension, float quality) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return pdfBytes;
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            boolean modified = false;

            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources != null) {
                    modified |= processResourcesForImages(document, resources, maxDimension, quality);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos, CompressParameters.DEFAULT_COMPRESSION);
            byte[] compressed = baos.toByteArray();

            if (compressed.length < pdfBytes.length) {
                log.info("[MediaCompressionUtil] Gentle downsampled PDF from {} to {} bytes (Saved {:.1f}%)",
                        pdfBytes.length, compressed.length,
                        ((pdfBytes.length - compressed.length) / (double) pdfBytes.length) * 100.0);
                return compressed;
            }
            return compressPdf(pdfBytes);
        } catch (Exception e) {
            log.warn("[MediaCompressionUtil] PDF downsampling skipped (invalid or encrypted PDF): {}", e.getMessage());
            return compressPdf(pdfBytes);
        }
    }

    private static boolean processResourcesForImages(PDDocument doc, PDResources resources, int maxDimension, float quality) {
        boolean modified = false;
        try {
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDImageXObject imgXObject) {
                        try {
                            BufferedImage originalImg = imgXObject.getImage();
                            if (originalImg == null) continue;

                            int w = originalImg.getWidth();
                            int h = originalImg.getHeight();

                            // Lewati elemen grafis sangat kecil (<100px) agar tidak terjadi distorsi ikon kecil
                            if (w < 100 && h < 100) continue;

                            boolean isAlreadyJpeg = imgXObject.getCOSObject().containsKey(COSName.FILTER)
                                    && imgXObject.getCOSObject().getItem(COSName.FILTER).toString().contains("DCTDecode");

                            // Jika sudah berformat JPEG dan resolusinya sudah <= maxDimension, lewati agar tidak blur berulang
                            if (isAlreadyJpeg && w <= maxDimension && h <= maxDimension) {
                                continue;
                            }

                            int targetW = w;
                            int targetH = h;
                            if (w > maxDimension || h > maxDimension) {
                                if (w >= h) {
                                    targetW = maxDimension;
                                    targetH = Math.max(1, (int) Math.round((double) h * maxDimension / w));
                                } else {
                                    targetH = maxDimension;
                                    targetW = Math.max(1, (int) Math.round((double) w * maxDimension / h));
                                }
                            }

                            boolean hasTransparency = hasActualTransparencyOrMask(imgXObject);

                            if (hasTransparency) {
                                // Preservasi transparansi (Slide 1 logo, bola dunia, cutout gedung):
                                // Render ke ARGB (jangan dicat putih!), lalu pisahkan kanal RGB dan Alpha mask (SMask)
                                BufferedImage resizedArgb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g = resizedArgb.createGraphics();
                                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                                g.drawImage(originalImg, 0, 0, targetW, targetH, null);
                                g.dispose();

                                BufferedImage rgbOnly = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
                                BufferedImage alphaOnly = new BufferedImage(targetW, targetH, BufferedImage.TYPE_BYTE_GRAY);

                                boolean foundTransparentPixel = false;
                                for (int y = 0; y < targetH; y++) {
                                    for (int x = 0; x < targetW; x++) {
                                        int argb = resizedArgb.getRGB(x, y);
                                        int a = (argb >>> 24) & 0xFF;
                                        if (a < 250) foundTransparentPixel = true;

                                        rgbOnly.setRGB(x, y, argb);
                                        int gray = (a << 16) | (a << 8) | a;
                                        alphaOnly.setRGB(x, y, gray);
                                    }
                                }

                                if (foundTransparentPixel) {
                                    // Kompresi RGB dengan JPEG (75-80%) dan SoftMask dengan Lossless FlateDecode
                                    PDImageXObject newRgbImg = JPEGFactory.createFromImage(doc, rgbOnly, quality);
                                    PDImageXObject newAlphaImg = LosslessFactory.createFromImage(doc, alphaOnly);
                                    newRgbImg.getCOSObject().setItem(COSName.SMASK, newAlphaImg.getCOSObject());
                                    resources.put(name, newRgbImg);
                                    modified = true;
                                } else {
                                    PDImageXObject newImg = JPEGFactory.createFromImage(doc, rgbOnly, quality);
                                    resources.put(name, newImg);
                                    modified = true;
                                }
                            } else {
                                // Gambar opaque tanpa transparansi (seperti slide penuh / dokumen scan)
                                BufferedImage resizedRgb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
                                Graphics2D g = resizedRgb.createGraphics();
                                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                                g.drawImage(originalImg, 0, 0, targetW, targetH, null);
                                g.dispose();

                                PDImageXObject newImg = JPEGFactory.createFromImage(doc, resizedRgb, quality);
                                resources.put(name, newImg);
                                modified = true;
                            }
                        } catch (Exception ex) {
                            // Abaikan gambar individual yang formatnya tidak didukung
                        }
                    }
                } else {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDFormXObject formXObject) {
                        PDResources formRes = formXObject.getResources();
                        if (formRes != null) {
                            modified |= processResourcesForImages(doc, formRes, maxDimension, quality);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MediaCompressionUtil] Error processing page resources for images: {}", e.getMessage());
        }
        return modified;
    }

    /**
     * Memeriksa apakah gambar di PDF memiliki Soft Mask (SMask), explicit Mask, atau Alpha Channel
     * yang secara visual digunakan untuk transparansi / cutout objek.
     */
    private static boolean hasActualTransparencyOrMask(PDImageXObject imgXObject) {
        try {
            // Cek Soft Mask (SMask)
            if (imgXObject.getCOSObject().containsKey(COSName.SMASK) || imgXObject.getSoftMask() != null) {
                PDImageXObject smask = imgXObject.getSoftMask();
                if (smask != null) {
                    BufferedImage maskImg = smask.getImage();
                    if (maskImg != null) {
                        int w = maskImg.getWidth();
                        int h = maskImg.getHeight();
                        // Sampling piksel untuk mengecek apakah ada bagian transparan (gray < 250)
                        int step = Math.max(1, Math.min(w, h) / 40);
                        for (int y = 0; y < h; y += step) {
                            for (int x = 0; x < w; x += step) {
                                int gray = maskImg.getRGB(x, y) & 0xFF;
                                if (gray < 250) {
                                    return true; // Ditemukan transparansi nyata
                                }
                            }
                        }
                    }
                } else {
                    return true;
                }
            }

            // Cek Explicit Mask
            if (imgXObject.getCOSObject().containsKey(COSName.MASK) || imgXObject.getMask() != null) {
                return true;
            }

            // Cek Alpha Channel pada BufferedImage
            BufferedImage bi = imgXObject.getImage();
            if (bi != null && bi.getColorModel().hasAlpha()) {
                int w = bi.getWidth();
                int h = bi.getHeight();
                int step = Math.max(1, Math.min(w, h) / 40);
                for (int y = 0; y < h; y += step) {
                    for (int x = 0; x < w; x += step) {
                        int pixel = bi.getRGB(x, y);
                        if ((pixel >>> 24) < 250) {
                            return true; // Ditemukan piksel transparan
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * OPTIMASI & KOMPRESI VIDEO MP4 (100% Lossless)
     * Mengoptimalkan struktur MP4 container:
     * - Menghilangkan padding box kosong (free / skip boxes) yang biasa disisipkan rekaman kamera HP.
     * - Mengatur FastStart (memindahkan moov header ke depan) agar video bisa langsung diputar dan dianalisis AI tanpa buffering.
     * - Menjaga seluruh frame visual H.264/H.265 dan audio AAC 100% utuh tanpa distorsi.
     */
    public static byte[] compressVideo(byte[] videoBytes) {
        if (videoBytes == null || videoBytes.length < 8) {
            return videoBytes;
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.wrap(videoBytes);

            byte[] ftypBox = null;
            byte[] moovBox = null;
            byte[] mdatBox = null;

            int originalLength = videoBytes.length;
            int offset = 0;

            // Parsing MP4 box/atom
            while (offset + 8 <= originalLength) {
                int boxSize = buffer.getInt(offset);
                byte[] typeBytes = new byte[4];
                buffer.position(offset + 4);
                buffer.get(typeBytes);
                String boxType = new String(typeBytes);

                if (boxSize <= 0) {
                    // Box extends to end of file or invalid
                    break;
                }

                int boxEnd = Math.min(originalLength, offset + boxSize);
                byte[] boxData = Arrays.copyOfRange(videoBytes, offset, boxEnd);

                if ("ftyp".equals(boxType)) {
                    ftypBox = boxData;
                } else if ("moov".equals(boxType)) {
                    moovBox = boxData;
                } else if ("mdat".equals(boxType)) {
                    mdatBox = boxData;
                } else if ("free".equals(boxType) || "skip".equals(boxType) || "wide".equals(boxType)) {
                    // Eliminasi box kosong / padding camera tanpa merusak video
                    log.info("[MediaCompressionUtil] Stripped redundant MP4 '{}' box ({} bytes)", boxType, boxSize);
                } else {
                    // Simpan box lain yang esensial
                    out.write(boxData);
                }

                offset += boxSize;
            }

            // Jika struktur MP4 terurai dengan baik (ftyp, moov, mdat)
            if (ftypBox != null && moovBox != null && mdatBox != null) {
                // Hitung pergeseran posisi mdat setelah reordering (delta offset)
                int mdatOffsetBefore = 0;
                int scanOffset = 0;
                while (scanOffset + 8 <= originalLength) {
                    int bSize = buffer.getInt(scanOffset);
                    byte[] tBytes = new byte[4];
                    buffer.position(scanOffset + 4);
                    buffer.get(tBytes);
                    if ("mdat".equals(new String(tBytes))) {
                        mdatOffsetBefore = scanOffset;
                        break;
                    }
                    if (bSize <= 0) break;
                    scanOffset += bSize;
                }

                int mdatOffsetAfter = ftypBox.length + moovBox.length;
                int delta = mdatOffsetAfter - mdatOffsetBefore;

                byte[] patchedMoov = Arrays.copyOf(moovBox, moovBox.length);
                if (delta != 0) {
                    patchStcoOffsets(patchedMoov, delta);
                }

                ByteArrayOutputStream optimizedMp4 = new ByteArrayOutputStream();
                // Urutkan FastStart: ftyp -> moov (metadata depan) -> mdat (data video) -> box lain
                optimizedMp4.write(ftypBox);
                optimizedMp4.write(patchedMoov);
                optimizedMp4.write(mdatBox);
                optimizedMp4.write(out.toByteArray());

                byte[] result = optimizedMp4.toByteArray();
                if (result.length < videoBytes.length) {
                    log.info("[MediaCompressionUtil] Optimized MP4 video from {} to {} bytes (Saved {:.1f}%)",
                            videoBytes.length, result.length,
                            ((videoBytes.length - result.length) / (double) videoBytes.length) * 100.0);
                    return result;
                }
            }

            return videoBytes;
        } catch (Exception e) {
            log.warn("[MediaCompressionUtil] Video optimization skipped: {}", e.getMessage());
            return videoBytes;
        }
    }

    private static void patchStcoOffsets(byte[] moovData, int delta) {
        if (delta == 0 || moovData == null) return;
        for (int i = 0; i <= moovData.length - 8; i++) {
            if (moovData[i] == 's' && moovData[i + 1] == 't' && moovData[i + 2] == 'c' && moovData[i + 3] == 'o') {
                try {
                    ByteBuffer buf = ByteBuffer.wrap(moovData);
                    buf.position(i + 4); // Lewati 'stco'
                    buf.getInt(); // version & flags
                    int entryCount = buf.getInt();
                    for (int e = 0; e < entryCount; e++) {
                        int pos = buf.position();
                        int oldOffset = buf.getInt();
                        buf.putInt(pos, oldOffset + delta);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
