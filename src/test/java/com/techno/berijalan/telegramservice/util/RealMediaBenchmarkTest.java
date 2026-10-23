package com.techno.berijalan.telegramservice.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RealMediaBenchmarkTest {

    private static File mediaTestDir;
    private static File compressOutputDir;

    record MediaItemResult(
            String fileName,
            long rawSizeBytes,
            long compSizeBytes,
            double reductionPercent
    ) {}

    @BeforeAll
    static void setUp() {
        File dir1 = new File("./MediaTest");
        File dir2 = new File("../MediaTest");
        if (dir1.exists() && dir1.isDirectory()) {
            mediaTestDir = dir1;
        } else if (dir2.exists() && dir2.isDirectory()) {
            mediaTestDir = dir2;
        } else {
            throw new IllegalStateException("Folder MediaTest tidak ditemukan di ./MediaTest atau ../MediaTest");
        }

        compressOutputDir = new File(mediaTestDir, "Compress");
        if (!compressOutputDir.exists()) {
            compressOutputDir.mkdirs();
        }
    }

    private static File findMediaFile(String fileName) {
        File direct = new File(mediaTestDir, fileName);
        if (direct.exists()) return direct;
        for (String sub : List.of("image", "pdf", "video")) {
            File subFile = new File(new File(mediaTestDir, sub), fileName);
            if (subFile.exists()) return subFile;
        }
        return null;
    }

    private static void saveCompressedFile(String fileName, byte[] bytes) {
        try {
            File directDest = new File(compressOutputDir, fileName);
            Files.write(directDest.toPath(), bytes);

            String subDir = "image";
            if (fileName.toLowerCase().endsWith(".pdf")) subDir = "pdf";
            else if (fileName.toLowerCase().endsWith(".mp4")) subDir = "video";

            File dir = new File(compressOutputDir, subDir);
            if (!dir.exists()) dir.mkdirs();
            Files.write(new File(dir, fileName).toPath(), bytes);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("BENCHMARK KONVERSI MEDIA: Tabel Perbandingan Ukuran Sebelum vs Sesudah")
    void testRealMediaFolderBenchmark() throws Exception {
        List<MediaItemResult> results = new ArrayList<>();

        String[] targetFiles = {
                "small size.jpg",
                "high size.jpg",
                "small size.pdf",
                "high size.pdf",
                "small size.mp4",
                "high size.mp4"
        };

        for (String name : targetFiles) {
            File file = findMediaFile(name);
            if (file == null || !file.exists()) continue;

            byte[] rawBytes = Files.readAllBytes(file.toPath());
            byte[] compBytes;

            if (name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".png")) {
                compBytes = MediaCompressionUtil.compressImage(rawBytes);
            } else if (name.toLowerCase().endsWith(".pdf")) {
                compBytes = MediaCompressionUtil.compressPdfDownsample(rawBytes);
            } else {
                compBytes = MediaCompressionUtil.compressVideo(rawBytes);
            }

            assertNotNull(compBytes);
            assertTrue(compBytes.length > 0);
            saveCompressedFile(name, compBytes);

            double reductionPercent = (1.0 - ((double) compBytes.length / rawBytes.length)) * 100.0;
            results.add(new MediaItemResult(name, rawBytes.length, compBytes.length, reductionPercent));
        }

        printResultTable(results);
    }

    private void printResultTable(List<MediaItemResult> results) {
        System.out.println();
        System.out.printf("%-18s | %-22s | %-24s | %-20s\n",
                "Nama Media", "Size Raw", "Size Compress", "Persentase Perbandingan");
        System.out.println("-".repeat(95));

        for (MediaItemResult r : results) {
            String rawStr = formatSize(r.rawSizeBytes()) + " (" + r.rawSizeBytes() + " B)";
            String compStr = formatSize(r.compSizeBytes()) + " (" + r.compSizeBytes() + " B)";
            System.out.printf("%-18s | %-22s | %-24s | -%.1f%%\n",
                    r.fileName(),
                    rawStr,
                    compStr,
                    r.reductionPercent()
            );
        }
        System.out.println();
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f KB", bytes / 1024.0);
        }
    }
}
