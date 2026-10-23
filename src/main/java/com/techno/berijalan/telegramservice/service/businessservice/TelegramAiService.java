package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramMessageDataService;
import com.techno.berijalan.telegramservice.util.MediaCompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramAiService {

    private final ChatModel chatModel;
    private final TelegramMessageDataService telegramMessageDataService;
    private final MinioStorageService minioStorageService;

    public String generateAiResponse(TelegramBucketEntity bucket, String latestCustomerMessage, int currentCycle) {
        log.info("Generating AI response for bucketId={}, cycle={}, messageLen={}", bucket.getId(), currentCycle, latestCustomerMessage.length());

        StringBuilder systemInstruction = new StringBuilder();
        systemInstruction.append("Anda adalah Customer Care Virtual resmi dari PT Astra Credit Companies (ACC) bernama AVA.\n")
                .append("Tugas Anda adalah melayani dan merespons pertanyaan nasabah seputar pembiayaan kredit kendaraan (mobil baru/bekas), simulasi cicilan, persyaratan dokumen, dan layanan pembiayaan secara ramah, profesional, sopan, dan ringkas dalam Bahasa Indonesia.\n")
                .append("Anda memiliki kemampuan multimodal untuk melihat, membaca, dan menganalisis lampiran media (foto gambar, dokumen PDF, dan video MP4) yang dikirimkan nasabah.\n")
                .append("- Jika nasabah melampirkan foto dokumen (seperti KTP, SIM, STNK, BPKB, slip gaji, struk) atau foto kendaraan, teliti dan jelaskan informasinya secara ramah dan profesional.\n")
                .append("- Jika nasabah melampirkan dokumen PDF, baca dan pahami isi dokumen tersebut lalu jawab pertanyaan nasabah dengan tepat.\n")
                .append("- Jika nasabah melampirkan video MP4, amati dan jelaskan apa yang tampak dalam video tersebut.\n")
                .append("- Jika nasabah mengirimkan beberapa file sekaligus, jawab dan rujuk setiap file sesuai dengan urutan dan nama filenya secara jelas.\n");

        if (currentCycle >= 5) {
            systemInstruction.append("\nPENTING: Ini adalah pesan balasan ke-5 (terakhir) sebelum percakapan dialihkan ke Customer Service / Agen manusia. ")
                    .append("Jawablah pertanyaan nasabah terlebih dahulu, lalu di bagian kalimat paling akhir respon Anda, Anda WAJIB menyampaikan kalimat penutup yang memberitahukan bahwa selanjutnya percakapan ini akan dialihkan dan ditangani langsung oleh Customer Service / Agen kami (contoh: \"Untuk penanganan lebih lanjut dan proses verifikasi, percakapan ini akan segera saya teruskan ke Customer Service / Agen kami ya kak. Mohon ditunggu sebentar, terima kasih! 😊\").\n");
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemInstruction.toString()));

        // Ambil riwayat pesan dalam room tiket ini
        List<TelegramMessageEntity> history = telegramMessageDataService.findByBucketIdOrderByTimestampAsc(bucket.getId());

        // Cari indeks balasan AI/Agent terakhir untuk menentukan batas giliran (turn) nasabah saat ini
        int lastBotReplyIdx = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            String sender = history.get(i).getSenderType();
            if ("AI".equalsIgnoreCase(sender) || "AGENT".equalsIgnoreCase(sender)) {
                lastBotReplyIdx = i;
                break;
            }
        }

        // Tambahkan riwayat pesan sebelumnya sebelum turn saat ini (maksimal 10 pesan)
        int historyEndIdx = lastBotReplyIdx >= 0 ? lastBotReplyIdx + 1 : 0;
        int startIdx = Math.max(0, historyEndIdx - 10);
        for (int i = startIdx; i < historyEndIdx; i++) {
            TelegramMessageEntity m = history.get(i);
            if ("CUSTOMER".equalsIgnoreCase(m.getSenderType()) && m.getMessageText() != null) {
                messages.add(new UserMessage(m.getMessageText()));
            } else if ("AI".equalsIgnoreCase(m.getSenderType()) && m.getMessageText() != null) {
                messages.add(new AssistantMessage(m.getMessageText()));
            }
        }

        // Kumpulkan media dari pesan-pesan nasabah pada turn saat ini (setelah bot reply terakhir)
        List<Media> mediaList = new ArrayList<>();
        for (int i = lastBotReplyIdx + 1; i < history.size(); i++) {
            TelegramMessageEntity m = history.get(i);
            if ("CUSTOMER".equalsIgnoreCase(m.getSenderType()) && StringUtils.hasText(m.getMediaUrl())) {
                Media media = loadMediaFromMinio(m);
                if (media != null) {
                    mediaList.add(media);
                }
            }
        }

        // Bentuk pesan terkini dari customer (teks + media)
        UserMessage.Builder userMsgBuilder = UserMessage.builder().text(latestCustomerMessage);
        if (!mediaList.isEmpty()) {
            userMsgBuilder.media(mediaList);
            log.info("Attached {} media item(s) to Gemini prompt for bucketId={}", mediaList.size(), bucket.getId());
        }
        messages.add(userMsgBuilder.build());

        try {
            ChatResponse response = chatModel.call(new Prompt(messages));
            String replyText = response.getResult().getOutput().getText();
            log.info("Gemini AI successfully generated response for bucketId={}, length={}", bucket.getId(), replyText != null ? replyText.length() : 0);
            return replyText;
        } catch (Exception e) {
            log.error("Failed to generate response from Gemini AI: {}", e.getMessage(), e);
            throw new RuntimeException("GEMINI_ERROR: " + e.getMessage(), e);
        }
    }

    private Media loadMediaFromMinio(TelegramMessageEntity m) {
        try {
            String objectName = extractObjectNameFromMediaUrl(m.getMediaUrl());
            if (!StringUtils.hasText(objectName)) {
                return null;
            }

            byte[] rawBytes = minioStorageService.downloadFile(objectName);
            if (rawBytes == null || rawBytes.length == 0) {
                return null;
            }

            String mediaType = m.getMediaType();
            String fileName = StringUtils.hasText(m.getFileName()) ? m.getFileName() : "file";

            if ("IMAGE".equalsIgnoreCase(mediaType)) {
                return Media.builder()
                        .mimeType(MimeType.valueOf("image/jpeg"))
                        .data(rawBytes)
                        .name(fileName)
                        .build();
            } else if ("DOCUMENT".equalsIgnoreCase(mediaType)) {
                // PDF dikompresi on-the-fly (Embedded Image Downsampling) khusus saat diarahkan ke Gemini AI
                // agar ukuran payload kecil dan respon inferensi 3x-5x lebih cepat
                byte[] compressed = MediaCompressionUtil.compressPdfDownsample(rawBytes);
                return Media.builder()
                        .mimeType(MimeType.valueOf("application/pdf"))
                        .data(compressed)
                        .name(fileName)
                        .build();
            } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
                return Media.builder()
                        .mimeType(MimeType.valueOf("video/mp4"))
                        .data(rawBytes)
                        .name(fileName)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to load media for AI from url '{}': {}", m.getMediaUrl(), e.getMessage());
        }
        return null;
    }

    private String extractObjectNameFromMediaUrl(String mediaUrl) {
        if (mediaUrl == null) return null;
        try {
            if (mediaUrl.contains("path=")) {
                String pathParam = mediaUrl.substring(mediaUrl.indexOf("path=") + 5);
                if (pathParam.contains("&")) {
                    pathParam = pathParam.substring(0, pathParam.indexOf('&'));
                }
                return URLDecoder.decode(pathParam, StandardCharsets.UTF_8);
            }
            return mediaUrl;
        } catch (Exception e) {
            return mediaUrl;
        }
    }
}
