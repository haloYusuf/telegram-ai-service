package com.techno.berijalan.telegramservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RealGeminiTokenBenchmarkTest {

    private static String apiKey;
    private static String modelName;
    private static File mediaTestDir;
    private static File compressDir;
    private static HttpClient httpClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_PROMPT = "Analisis media ini";

    record AiCallResult(
            String fileName,
            String variant,
            long fileSizeBytes,
            long latencyMs,
            int promptTokens,
            int candidateTokens,
            int totalTokens,
            String responseText
    ) {}

    record BenchmarkComparison(
            String category,
            String fileName,
            AiCallResult rawResult,
            AiCallResult compResult,
            double tokenSavingPercent,
            double sizeSavingPercent,
            double speedupFactor
    ) {}

    record MediaTarget(String fileName, String category, String mimeType) {}

    @BeforeAll
    static void setUp() throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        apiKey = System.getenv("GEMINI_API_KEY");
        modelName = System.getenv("GEMINI_MODEL");

        if (apiKey == null || apiKey.isBlank()) {
            File[] envCandidates = { new File(".env"), new File("../.env"), new File("../../.env") };
            for (File envFile : envCandidates) {
                if (envFile.exists()) {
                    List<String> lines = Files.readAllLines(envFile.toPath());
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("GEMINI_API_KEY=")) {
                            apiKey = line.substring("GEMINI_API_KEY=".length()).trim();
                        }
                        if (line.startsWith("GEMINI_MODEL=") && (modelName == null || modelName.isBlank())) {
                            modelName = line.substring("GEMINI_MODEL=".length()).trim();
                        }
                    }
                    if (apiKey != null && !apiKey.isBlank()) break;
                }
            }
        }

        if (modelName == null || modelName.isBlank()) {
            modelName = "gemini-3.1-flash-lite";
        }

        assertNotNull(apiKey, "GEMINI_API_KEY wajib ditemukan di environment atau file .env");

        File dir1 = new File("./MediaTest");
        File dir2 = new File("../MediaTest");
        if (dir1.exists() && dir1.isDirectory()) {
            mediaTestDir = dir1;
        } else if (dir2.exists() && dir2.isDirectory()) {
            mediaTestDir = dir2;
        } else {
            throw new IllegalStateException("Direktori MediaTest tidak ditemukan!");
        }

        compressDir = new File(mediaTestDir, "Compress");
        if (!compressDir.exists()) {
            compressDir.mkdirs();
        }
    }

    @Test
    @DisplayName("BENCHMARK NYATA GEMINI AI: Token & Kualitas Respon untuk Seluruh Berkas MediaTest")
    void testCompareAllMediaGeminiTokensAndResponses() throws Exception {
        List<BenchmarkComparison> comparisons = new ArrayList<>();

        List<MediaTarget> targets = List.of(
                new MediaTarget("small size.jpg", "IMAGE", "image/jpeg"),
                new MediaTarget("high size.jpg", "IMAGE", "image/jpeg"),
                new MediaTarget("small size.pdf", "PDF", "application/pdf"),
                new MediaTarget("high size.pdf", "PDF", "application/pdf"),
                new MediaTarget("small size.mp4", "VIDEO", "video/mp4"),
                new MediaTarget("high size.mp4", "VIDEO", "video/mp4")
        );

        for (MediaTarget target : targets) {
            File rawFile = findRawFile(target.fileName(), target.category());
            if (rawFile == null || !rawFile.exists()) continue;

            File compFile = findCompFile(target.fileName(), target.category());
            byte[] compBytes;
            if (compFile != null && compFile.exists()) {
                compBytes = Files.readAllBytes(compFile.toPath());
            } else {
                byte[] rBytes = Files.readAllBytes(rawFile.toPath());
                if ("IMAGE".equalsIgnoreCase(target.category())) {
                    compBytes = MediaCompressionUtil.compressImage(rBytes);
                } else if ("PDF".equalsIgnoreCase(target.category())) {
                    compBytes = MediaCompressionUtil.compressPdfDownsample(rBytes);
                } else {
                    compBytes = MediaCompressionUtil.compressVideo(rBytes);
                }
                File savedComp = new File(compressDir, target.fileName());
                Files.write(savedComp.toPath(), compBytes);
            }

            byte[] rawBytes = Files.readAllBytes(rawFile.toPath());

            try {
                AiCallResult rawResult = callGeminiWithRetry(target.fileName(), "RAW", rawBytes, target.mimeType());
                Thread.sleep(4000);

                AiCallResult compResult = callGeminiWithRetry(target.fileName(), "COMPRESSED", compBytes, target.mimeType());
                Thread.sleep(4000);

                double tokenSaving = 0.0;
                if (rawResult.totalTokens() > 0) {
                    tokenSaving = ((double) (rawResult.totalTokens() - compResult.totalTokens()) / rawResult.totalTokens()) * 100.0;
                }

                double sizeSaving = ((double) (rawBytes.length - compBytes.length) / rawBytes.length) * 100.0;
                double speedup = compResult.latencyMs() > 0 ? (double) rawResult.latencyMs() / compResult.latencyMs() : 1.0;

                comparisons.add(new BenchmarkComparison(target.category(), target.fileName(), rawResult, compResult, tokenSaving, sizeSaving, speedup));
            } catch (Exception ex) {
                System.err.println("Gagal memproses perbandingan " + target.fileName() + ": " + ex.getMessage());
            }
        }

        printAiBenchmarkTable(comparisons);
    }

    private File findRawFile(String fileName, String category) {
        File direct = new File(mediaTestDir, fileName);
        if (direct.exists()) return direct;
        File sub = new File(new File(mediaTestDir, category.toLowerCase()), fileName);
        if (sub.exists()) return sub;
        return null;
    }

    private File findCompFile(String fileName, String category) {
        File direct = new File(compressDir, fileName);
        if (direct.exists()) return direct;
        File sub = new File(new File(compressDir, category.toLowerCase()), fileName);
        if (sub.exists()) return sub;
        return null;
    }

    private AiCallResult callGeminiWithRetry(String fileName, String variant, byte[] mediaBytes, String mimeType) throws Exception {
        int maxAttempts = 3;
        String b64Data = Base64.getEncoder().encodeToString(mediaBytes);

        String endpoint = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", modelName, apiKey);

        Map<String, Object> textPart = Map.of("text", DEFAULT_PROMPT);
        Map<String, Object> inlineData = Map.of("mime_type", mimeType, "data", b64Data);
        Map<String, Object> mediaPart = Map.of("inline_data", inlineData);

        Map<String, Object> contentObj = Map.of("parts", List.of(textPart, mediaPart));
        Map<String, Object> payload = Map.of("contents", List.of(contentObj));

        String jsonPayload = objectMapper.writeValueAsString(payload);

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long tStart = System.currentTimeMillis();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(90))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long latency = System.currentTimeMillis() - tStart;

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());

                    String responseText = "";
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (parts.isArray() && !parts.isEmpty()) {
                            responseText = parts.get(0).path("text").asText("");
                        }
                    }

                    JsonNode usage = root.path("usageMetadata");
                    int promptTokens = usage.path("promptTokenCount").asInt(0);
                    int candidateTokens = usage.path("candidatesTokenCount").asInt(0);
                    int totalTokens = usage.path("totalTokenCount").asInt(0);

                    return new AiCallResult(fileName, variant, mediaBytes.length, latency, promptTokens, candidateTokens, totalTokens, responseText);
                } else if (response.statusCode() == 503 || response.statusCode() == 429) {
                    Thread.sleep(4000);
                } else {
                    throw new RuntimeException("Gemini API error (HTTP " + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    Thread.sleep(4000);
                }
            }
        }
        throw new RuntimeException("Gagal memanggil Gemini API setelah " + maxAttempts + " percobaan: " + (lastException != null ? lastException.getMessage() : "Unknown"));
    }

    private void printAiBenchmarkTable(List<BenchmarkComparison> comparisons) {
        System.out.println();
        System.out.printf("%-16s | %-10s | %-11s | %-10s | %-11s | %-26s | %-50s\n",
                "Nama Media", "Versi", "Token Req", "Lama Req", "Token Res", "Perbandingan", "Hasil Response");
        System.out.println("-".repeat(140));

        for (BenchmarkComparison c : comparisons) {
            String speedupStr = String.format("Compress %.1fx Lebih Cepat", c.speedupFactor());
            if (c.speedupFactor() < 1.0) {
                speedupStr = String.format("Raw %.1fx Lebih Cepat", 1.0 / c.speedupFactor());
            }

            String rawSnippet = c.rawResult().responseText().replaceAll("\\s+", " ").trim();
            if (rawSnippet.length() > 47) rawSnippet = rawSnippet.substring(0, 44) + "...";

            String compSnippet = c.compResult().responseText().replaceAll("\\s+", " ").trim();
            if (compSnippet.length() > 47) compSnippet = compSnippet.substring(0, 44) + "...";

            // Baris RAW
            System.out.printf("%-16s | %-10s | %8d tk | %7.2f s | %8d tk | %-26s | %-50s\n",
                    c.fileName(),
                    "Raw",
                    c.rawResult().promptTokens(),
                    c.rawResult().latencyMs() / 1000.0,
                    c.rawResult().candidateTokens(),
                    "-",
                    rawSnippet
            );

            // Baris COMPRESS
            System.out.printf("%-16s | %-10s | %8d tk | %7.2f s | %8d tk | %-26s | %-50s\n",
                    c.fileName(),
                    "Compress",
                    c.compResult().promptTokens(),
                    c.compResult().latencyMs() / 1000.0,
                    c.compResult().candidateTokens(),
                    speedupStr,
                    compSnippet
            );
            System.out.println("-".repeat(140));
        }
        System.out.println();
    }
}
