package com.techno.berijalan.telegramservice.service.businessservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techno.berijalan.telegramservice.domain.dto.response.ResMediaUploadDto;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramApiResponse;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramFileDto;
import com.techno.berijalan.telegramservice.rest.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.techno.berijalan.telegramservice.util.MediaCompressionUtil;
import org.apache.tika.Tika;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMediaService {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    public static final long BASE64_MAX_BYTES = 5L * 1024 * 1024;      // 5MB

    private final TelegramApiClient telegramApiClient;
    private final MinioStorageService minioStorageService;
    private final Tika tika = new Tika();

    @Value("${telegram.api-url}")
    private String telegramApiUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Mengunduh file media nasabah dari Telegram Cloud berdasarkan fileId.
     * Maksimal ukuran file 10MB.
     */
    public byte[] downloadTelegramFile(String botToken, String fileId) {
        log.info("[TelegramMedia] Requesting file metadata for fileId: {}", fileId);
        TelegramApiResponse<TelegramFileDto> response = telegramApiClient.getFile(botToken, fileId);

        if (response == null || !Boolean.TRUE.equals(response.getOk()) || response.getResult() == null) {
            throw new RuntimeException("Gagal mengambil informasi file dari Telegram: response null atau tidak ok");
        }

        TelegramFileDto fileDto = response.getResult();
        if (fileDto.getFileSize() != null && fileDto.getFileSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Ukuran file Telegram (" + fileDto.getFileSize() + " bytes) melebihi batas maksimal 10MB!");
        }

        String filePath = fileDto.getFilePath();
        if (!StringUtils.hasText(filePath)) {
            throw new RuntimeException("Telegram file_path kosong untuk fileId: " + fileId);
        }

        String downloadUrl = telegramApiUrl + "/file/bot" + botToken + "/" + filePath;
        log.info("[TelegramMedia] Downloading binary from Telegram: {}", downloadUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();

            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("Telegram Cloud mengembalikan status " + httpResponse.statusCode() + " saat mengunduh file");
            }

            byte[] bytes = httpResponse.body();
            if (bytes.length > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("Ukuran file terunduh (" + bytes.length + " bytes) melebihi batas maksimal 10MB!");
            }

            log.info("[TelegramMedia] Successfully downloaded {} bytes for fileId: {}", bytes.length, fileId);
            return bytes;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TelegramMedia] Error downloading file from Telegram: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal mengunduh file dari Telegram Cloud: " + e.getMessage(), e);
        }
    }

    /**
     * Mengirim file media (dokumen, foto, atau video) ke Telegram Cloud dengan Multipart/Form-Data.
     */
    public Long sendMediaToTelegram(String botToken, Long chatId, String mediaType, byte[] fileBytes, String fileName, String caption) {
        return sendMediaToTelegram(botToken, chatId, mediaType, fileBytes, fileName, caption, null);
    }

    /**
     * Mengirim file media dengan dukungan reply_to_message_id dan reply_parameters ke Telegram Cloud.
     * Mengembalikan telegram message_id yang dihasilkan oleh Telegram Cloud.
     */
    public Long sendMediaToTelegram(String botToken, Long chatId, String mediaType, byte[] fileBytes, String fileName, String caption, Long replyToTelegramMessageId) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("Data file media tidak boleh kosong!");
        }
        if (fileBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Ukuran media (" + fileBytes.length + " bytes) melebihi batas maksimal 10MB!");
        }

        String normalizedType = (mediaType != null) ? mediaType.toUpperCase() : "DOCUMENT";
        String endpoint;
        String formParamName = switch (normalizedType) {
            case "IMAGE", "PHOTO" -> {
                endpoint = "/sendPhoto";
                yield "photo";
            }
            case "VIDEO" -> {
                endpoint = "/sendVideo";
                yield "video";
            }
            default -> {
                endpoint = "/sendDocument";
                yield "document";
            }
        };

        String finalFileName = StringUtils.hasText(fileName) ? fileName : ("file_" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId.toString());

        if (StringUtils.hasText(caption)) {
            body.add("caption", caption.trim());
        }

        if (replyToTelegramMessageId != null) {
            body.add("reply_to_message_id", replyToTelegramMessageId.toString());
            body.add("reply_parameters", "{\"message_id\":" + replyToTelegramMessageId + ",\"allow_sending_without_reply\":true}");
        }

        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return finalFileName;
            }
        };
        body.add(formParamName, resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        String targetUrl = telegramApiUrl + "/bot" + botToken + endpoint;

        try {
            log.info("[TelegramMedia] Dispatching multipart {} to Telegram Cloud (chatId={}, size={} bytes, fileName='{}', replyTo={})",
                    endpoint, chatId, fileBytes.length, finalFileName, replyToTelegramMessageId);
            String responseStr = restTemplate.postForObject(targetUrl, requestEntity, String.class);
            log.info("[TelegramMedia] Successfully dispatched media to Telegram Cloud: {}", responseStr);

            if (responseStr != null && responseStr.contains("\"message_id\":")) {
                JsonNode json = new ObjectMapper().readTree(responseStr);
                if (json.has("result") && json.get("result").has("message_id")) {
                    return json.get("result").get("message_id").asLong();
                }
            }
        } catch (Exception e) {
            log.error("[TelegramMedia] Failed to send media to Telegram Cloud: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal mengirim media ke Telegram Cloud: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Memperbarui foto profil bot di Telegram Cloud menggunakan endpoint setMyProfilePhoto.
     * Menggunakan multipart/form-data dengan field 'photo' (JSON string) dan 'bot_photo' (binary file).
     */
    public void setBotProfilePhoto(String botToken, byte[] photoBytes, String fileName) {
        if (photoBytes == null || photoBytes.length == 0) {
            throw new IllegalArgumentException("Data file foto bot tidak boleh kosong!");
        }
        if (photoBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Ukuran foto profil bot (" + photoBytes.length + " bytes) melebihi batas maksimal 10MB!");
        }

        String finalFileName = StringUtils.hasText(fileName) ? fileName : "bot_avatar.jpg";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("photo", "{\"type\":\"static\",\"photo\":\"attach://bot_photo\"}");

        ByteArrayResource resource = new ByteArrayResource(photoBytes) {
            @Override
            public String getFilename() {
                return finalFileName;
            }
        };
        body.add("bot_photo", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        String targetUrl = telegramApiUrl + "/bot" + botToken + "/setMyProfilePhoto";

        try {
            log.info("[TelegramMedia] Uploading profile photo to Telegram Cloud (size={} bytes, fileName='{}')",
                    photoBytes.length, finalFileName);
            String responseStr = restTemplate.postForObject(targetUrl, requestEntity, String.class);
            log.info("[TelegramMedia] Telegram setMyProfilePhoto response: {}", responseStr);
        } catch (Exception e) {
            log.error("[TelegramMedia] Failed to set bot profile photo on Telegram Cloud: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal memperbarui foto profil di Telegram Cloud: " + e.getMessage(), e);
        }
    }

    /**
     * Memproses upload file media ke MinIO (maksimal 10MB) dan mengembalikan informasi media upload.
     */
    public ResMediaUploadDto uploadMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File yang diunggah tidak boleh kosong!");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File ditolak karena terlalu besar (batas maksimal 10 mb)");
        }

        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "file";
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = minioStorageService.determineContentType(originalName);
        }

        // Validasi: hanya foto (JPG/PNG/WEBP), dokumen PDF, dan video MP4 yang diperbolehkan
        String mediaType = determineMediaType(originalName, contentType);
        if (mediaType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File ditolak karena format nya tidak valid");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal membaca berkas: " + e.getMessage());
        }

        // Validasi Magic Bytes biner via Apache Tika untuk mencegah extension spoofing (.exe/.sh direname jadi .jpg/.pdf)
        if (!isValidMediaBytes(fileBytes, originalName)) {
            log.warn("[TelegramMediaService] File upload rejected by Apache Tika: filename='{}', declaredContentType='{}'",
                    originalName, contentType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File ditolak karena format nya tidak valid");
        }

        // Inbound Eager Compression (Foto maks 1280px/JPEG 80%, PDF FlateDecode Lossless, MP4 FastStart)
        byte[] bytesToUpload = MediaCompressionUtil.compressMedia(fileBytes, mediaType);
        long compressedSize = bytesToUpload.length;

        String sanitizedFilename = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectName = "uploads/" + UUID.randomUUID() + "_" + sanitizedFilename;

        try {
            minioStorageService.uploadFile(objectName, bytesToUpload, contentType);
            log.info("[TelegramMediaService] Inbound compressed and uploaded media to MinIO: {} -> {} bytes", file.getSize(), compressedSize);
        } catch (Exception e) {
            log.error("[TelegramMediaService] Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal mengunggah file: " + e.getMessage());
        }

        String streamUrl = minioStorageService.buildStreamUrl(objectName);
        return ResMediaUploadDto.builder()
                .mediaUrl(streamUrl)
                .minioPath(objectName)
                .fileName(originalName)
                .fileSize(compressedSize)
                .mediaType(mediaType)
                .build();
    }

    /**
     * Mengunduh byte media dari MinIO dan menyusun HTTP Headers untuk stream/download.
     */
    public ResponseEntity<byte[]> streamMedia(String path, boolean download) {
        if (!StringUtils.hasText(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path media wajib diisi!");
        }

        try {
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            byte[] fileBytes = minioStorageService.downloadFile(decodedPath);
            String contentType = minioStorageService.determineContentType(decodedPath);

            String filename = decodedPath.substring(decodedPath.lastIndexOf('/') + 1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(fileBytes.length);
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("[TelegramMediaService] Error streaming media from path '{}': {}", path, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media tidak ditemukan atau gagal dimuat: " + e.getMessage());
        }
    }

    public String determineMediaType(String fileName, String contentType) {
        String lowerCt = (contentType != null) ? contentType.toLowerCase() : "";
        String lowerName = (fileName != null) ? fileName.toLowerCase() : "";

        // Foto: JPG, JPEG, PNG, WEBP
        if (lowerCt.equals("image/jpeg") || lowerCt.equals("image/png") || lowerCt.equals("image/webp")
                || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".webp")) {
            return "IMAGE";
        }
        // Video: MP4
        if (lowerCt.equals("video/mp4") || lowerName.endsWith(".mp4")) {
            return "VIDEO";
        }
        // Dokumen: PDF
        if (lowerCt.equals("application/pdf") || lowerName.endsWith(".pdf")) {
            return "DOCUMENT";
        }
        return null;
    }

    /**
     * Mendeteksi tipe MIME sesungguhnya dari byte biner menggunakan Apache Tika (Magic Bytes inspection).
     */
    public String detectMimeType(byte[] fileBytes, String originalFileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            return "application/octet-stream";
        }
        try {
            return tika.detect(fileBytes, originalFileName);
        } catch (Exception e) {
            log.warn("[TelegramMediaService] Failed to detect MIME type via Tika: {}", e.getMessage());
            return "application/octet-stream";
        }
    }

    /**
     * Memvalidasi apakah byte biner aman dan bukan file executable berbahaya (.exe, .sh, .bat, elf, dll).
     * Mencegah File Extension Spoofing (misal file malware di-rename menjadi .jpg / .pdf).
     */
    public boolean isValidMediaBytes(byte[] fileBytes, String originalFileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            return false;
        }

        // 1. Blacklist tanda tangan biner executable (Windows PE 'MZ', Linux ELF)
        if (fileBytes.length >= 2 && fileBytes[0] == 0x4D && fileBytes[1] == 0x5A) {
            log.warn("[TelegramMediaService] Rejected file '{}': detected Windows PE/MZ executable signature!", originalFileName);
            return false;
        }
        if (fileBytes.length >= 4 && fileBytes[0] == 0x7F && fileBytes[1] == 0x45 && fileBytes[2] == 0x4C && fileBytes[3] == 0x46) {
            log.warn("[TelegramMediaService] Rejected file '{}': detected Linux ELF executable signature!", originalFileName);
            return false;
        }

        // 2. Deteksi MIME via Apache Tika
        String detectedMime = detectMimeType(fileBytes, originalFileName).toLowerCase();
        log.info("[TelegramMediaService] Apache Tika detected binary MIME: '{}' for file: '{}'", detectedMime, originalFileName);

        // Blacklist MIME berbahaya
        if (detectedMime.contains("dosexec") || detectedMime.contains("x-executable")
                || detectedMime.contains("x-sh") || detectedMime.contains("x-bat")
                || detectedMime.contains("x-msdownload") || detectedMime.contains("x-msdos-program")
                || detectedMime.contains("x-sharedlib") || detectedMime.contains("x-shellscript")) {
            log.warn("[TelegramMediaService] Rejected file '{}': detected dangerous executable MIME '{}'", originalFileName, detectedMime);
            return false;
        }

        return true;
    }
}

