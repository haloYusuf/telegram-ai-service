package com.techno.berijalan.telegramservice.service.businessservice.impl;

import com.techno.berijalan.telegramservice.domain.dto.TelegramUpdateDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatEventDto;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramSessionEntity;
import com.techno.berijalan.telegramservice.rest.TelegramApiClient;
import com.techno.berijalan.telegramservice.service.businessservice.MinioStorageService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramAiBufferService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramMediaService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramSseEmitterService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramWebhookBusinessService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBotDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBucketDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramMessageDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramSessionDataService;
import com.techno.berijalan.telegramservice.util.MediaCompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookBusinessServiceImpl implements TelegramWebhookBusinessService {

    public static final String TELEGRAM_UPDATE_PREFIX = "telegram_update::";
    public static final Duration UPDATE_ID_TTL = Duration.ofMinutes(10);

    private final TelegramBotDataService telegramBotDataService;
    private final TelegramSessionDataService telegramSessionDataService;
    private final TelegramBucketDataService telegramBucketDataService;
    private final TelegramMessageDataService telegramMessageDataService;
    private final TelegramSseEmitterService telegramSseEmitterService;
    private final TelegramAiBufferService telegramAiBufferService;
    private final TelegramMediaService telegramMediaService;
    private final MinioStorageService minioStorageService;
    private final TelegramApiClient telegramApiClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void processIncomingUpdate(String botId, TelegramUpdateDto updateDto) {
        // 1. Deduplikasi update_id via Redis untuk mencegah pemrosesan ganda akibat retry webhook Telegram
        if (updateDto != null && updateDto.getUpdateId() != null) {
            try {
                String deduplicationKey = TELEGRAM_UPDATE_PREFIX + botId + "::" + updateDto.getUpdateId();
                Boolean isNewUpdate = stringRedisTemplate.opsForValue().setIfAbsent(deduplicationKey, "1", UPDATE_ID_TTL);
                if (Boolean.FALSE.equals(isNewUpdate)) {
                    log.warn("Ignored duplicate Telegram webhook update (already processed or currently processing): botId={}, updateId={}",
                            botId, updateDto.getUpdateId());
                    return;
                }
            } catch (Exception e) {
                log.error("Failed to perform Redis deduplication check for botId={}, updateId={}: {}", botId, updateDto.getUpdateId(), e.getMessage());
            }
        }

        // 2. Verifikasi apakah bot terdaftar dan aktif
        Optional<TelegramBotEntity> botOpt = telegramBotDataService.findByIdAndIsActiveTrue(botId);
        if (botOpt.isEmpty()) {
            log.warn("Ignored webhook: Bot with id={} is not found or inactive", botId);
            return;
        }

        TelegramBotEntity bot = botOpt.get();

        // 2. Validasi update message dari Telegram
        if (updateDto == null || updateDto.getMessage() == null || updateDto.getMessage().getChat() == null) {
            log.info("Ignored non-message update for bot {}: updateId={}", botId, updateDto != null ? updateDto.getUpdateId() : "null");
            return;
        }

        TelegramUpdateDto.MessageDto message = updateDto.getMessage();
        Long telegramChatId = message.getChat().getId();

        // Profil nasabah
        String customerFirstName = (message.getFrom() != null && StringUtils.hasText(message.getFrom().getFirstName()))
                ? message.getFrom().getFirstName().trim()
                : "Customer";
        String customerUsername = message.getFrom() != null ? message.getFrom().getUsername() : null;
        String phoneNumber = message.getContact() != null ? message.getContact().getPhoneNumber() : null;

        // Identifikasi Tipe Media (Dokumen, Foto, Video)
        String mediaType = "TEXT";
        String fileId = null;
        String fileName = null;
        Long fileSize = null;
        String mimeType = null;
        boolean isFileMessage = false;

        if (message.getPhoto() != null && !message.getPhoto().isEmpty()) {
            isFileMessage = true;
            // Ambil foto dengan resolusi tertinggi (elemen terakhir pada array)
            TelegramUpdateDto.PhotoSizeDto photoSize = message.getPhoto().getLast();
            mediaType = "IMAGE";
            fileId = photoSize.getFileId();
            fileSize = photoSize.getFileSize();
            fileName = "photo_" + fileId + ".jpg";
            mimeType = "image/jpeg";
        } else if (message.getDocument() != null) {
            isFileMessage = true;
            TelegramUpdateDto.DocumentDto doc = message.getDocument();
            mediaType = "DOCUMENT";
            fileId = doc.getFileId();
            fileSize = doc.getFileSize();
            fileName = StringUtils.hasText(doc.getFileName()) ? doc.getFileName() : ("doc_" + fileId);
            mimeType = StringUtils.hasText(doc.getMimeType()) ? doc.getMimeType() : "application/octet-stream";
        } else if (message.getVideo() != null) {
            isFileMessage = true;
            TelegramUpdateDto.VideoDto video = message.getVideo();
            mediaType = "VIDEO";
            fileId = video.getFileId();
            fileSize = video.getFileSize();
            fileName = StringUtils.hasText(video.getFileName()) ? video.getFileName() : ("video_" + fileId + ".mp4");
            mimeType = StringUtils.hasText(video.getMimeType()) ? video.getMimeType() : "video/mp4";
        }

        // Validasi format file untuk AI: Hanya Foto (JPG/PNG/WEBP), Dokumen PDF (.pdf), dan Video MP4 (.mp4)
        boolean isOversized = (fileSize != null && fileSize > TelegramMediaService.MAX_FILE_SIZE_BYTES);
        boolean isValidFormat = false;

        if (isFileMessage) {
            String lowerName = (fileName != null) ? fileName.toLowerCase() : "";
            String lowerMime = (mimeType != null) ? mimeType.toLowerCase() : "";

            switch (mediaType) {
                case "IMAGE" -> isValidFormat = true;
                case "DOCUMENT" -> {
                    if (lowerName.endsWith(".pdf") || lowerMime.contains("pdf")) {
                        mediaType = "DOCUMENT";
                        mimeType = "application/pdf";
                        isValidFormat = true;
                    } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerMime.startsWith("image/")) {
                        mediaType = "IMAGE";
                        isValidFormat = true;
                    } else if (lowerName.endsWith(".mp4") || lowerMime.contains("mp4")) {
                        mediaType = "VIDEO";
                        mimeType = "video/mp4";
                        isValidFormat = true;
                    }
                }
                case "VIDEO" -> {
                    if (lowerName.endsWith(".mp4") || lowerMime.contains("mp4")) {
                        isValidFormat = true;
                    }
                }
            }
        }

        // Teks atau Caption
        String caption = message.getCaption();
        String text = message.getText();



        // 3. Kelola Sesi Nasabah (TelegramSessionEntity)
        Optional<TelegramSessionEntity> sessionOpt = telegramSessionDataService.findFirstByBotIdAndTelegramChatId(bot.getId(), telegramChatId);
        TelegramSessionEntity session;
        if (sessionOpt.isPresent()) {
            session = sessionOpt.get();
            session.setLastInteractionTime(new Date());
        } else {
            session = TelegramSessionEntity.builder()
                    .botId(bot.getId())
                    .tenantId(bot.getTenantId())
                    .telegramChatId(telegramChatId)
                    .qnaCount(0)
                    .isTakeover(true)
                    .lastInteractionTime(new Date())
                    .build();
        }

        // 4. Kelola Tiket / Room Chat (TelegramBucketEntity)
        Optional<TelegramBucketEntity> activeBucketOpt = telegramBucketDataService
                .findFirstByBotIdAndTelegramChatIdAndStatusNot(bot.getId(), telegramChatId, "RESOLVED");

        boolean isNewOrReopenedBucket = false;
        TelegramBucketEntity bucket;
        if (activeBucketOpt.isPresent()) {
            bucket = activeBucketOpt.get();
        } else {
            // Cek apakah nasabah memiliki tiket sebelumnya yang berstatus RESOLVED untuk di-reopen otomatis
            Optional<TelegramBucketEntity> previousBucketOpt = telegramBucketDataService
                    .findFirstByBotIdAndTelegramChatIdOrderByUpdatedAtDesc(bot.getId(), telegramChatId);

            if (previousBucketOpt.isPresent()) {
                bucket = previousBucketOpt.get();
                bucket.setStatus("AI_HANDLING");
                isNewOrReopenedBucket = true;
                log.info("Auto-reopened resolved bucket {} into AI_HANDLING mode because customer sent a new message", bucket.getId());
            } else {
                bucket = TelegramBucketEntity.builder()
                        .botId(bot.getId())
                        .tenantId(bot.getTenantId())
                        .telegramChatId(telegramChatId)
                        .customerFirstName(customerFirstName)
                        .customerUsername(customerUsername)
                        .customerPhoneNumber(phoneNumber)
                        .status("AI_HANDLING")
                        .createdAt(new Date())
                        .build();
                isNewOrReopenedBucket = true;
                log.info("Created new customer care bucket in AI_HANDLING mode for customer '{}' on bot @{}", customerFirstName, bot.getBotUsername());
            }
        }

        // Auto-Sync jika nama profil nasabah berubah di Telegram
        boolean profileChanged = false;
        if (!customerFirstName.equals(bucket.getCustomerFirstName())) {
            bucket.setCustomerFirstName(customerFirstName);
            profileChanged = true;
        }
        if (customerUsername != null && !customerUsername.equals(bucket.getCustomerUsername())) {
            bucket.setCustomerUsername(customerUsername);
            profileChanged = true;
        }
        if (phoneNumber != null && !phoneNumber.equals(bucket.getCustomerPhoneNumber())) {
            bucket.setCustomerPhoneNumber(phoneNumber);
            profileChanged = true;
        }

        if (profileChanged) {
            log.info("Customer profile updated for bucket {}: name='{}', username=@{}", bucket.getId(), customerFirstName, customerUsername);
        }

        bucket.setUpdatedAt(new Date());
        bucket = telegramBucketDataService.save(bucket);

        if (isNewOrReopenedBucket && bucket.getId() != null) {
            telegramAiBufferService.initCycle(bucket.getId());
        }

        // Simpan bucketId aktif pada sesi
        session.setActiveBucketId(bucket.getId());
        telegramSessionDataService.save(session);

        // 5. Validasi Penolakan Pesan / Media yang Tidak Didukung (Bukan Foto, Dokumen, Video, atau Teks)
        if ("TEXT".equals(mediaType) && !StringUtils.hasText(text)) {
            String rejectionText = "Format media tidak valid dan tidak akan diproses";
            log.warn("Rejected unsupported media type (bukan foto, dokumen, atau video) from chatId={} on bucket={}", telegramChatId, bucket.getId());

            // Perpanjang/reset timer debounce Redis (5s) tanpa memasukkan pesan ke buffer
            telegramAiBufferService.resetTimerOnly(bucket.getId());

            // Kirim pesan balasan penolakan ke Telegram Cloud nasabah
            try {
                telegramApiClient.sendMessage(bot.getBotToken(), Map.of(
                        "chat_id", telegramChatId,
                        "text", rejectionText
                ));
            } catch (Exception e) {
                log.error("Failed to send unsupported media notice to Telegram customer: {}", e.getMessage());
            }

            // Simpan pesan penolakan AI ke database & broadcast SSE
            TelegramMessageEntity rejectionEntity = TelegramMessageEntity.builder()
                    .botId(bot.getId())
                    .tenantId(bot.getTenantId())
                    .bucketId(bucket.getId())
                    .telegramChatId(telegramChatId)
                    .senderType("AI")
                    .senderName("AVA")
                    .messageText(rejectionText)
                    .mediaType("TEXT")
                    .timestamp(new Date())
                    .build();
            TelegramMessageEntity savedRejection = telegramMessageDataService.save(rejectionEntity);

            ResTelegramChatEventDto eventDto = ResTelegramChatEventDto.builder()
                    .eventType("NEW_MESSAGE")
                    .botId(bot.getId())
                    .bucketId(bucket.getId())
                    .telegramChatId(telegramChatId)
                    .messageId(savedRejection != null ? savedRejection.getId() : null)
                    .senderType("AI")
                    .senderName("AVA")
                    .messageText(rejectionText)
                    .mediaType("TEXT")
                    .timestamp(new Date())
                    .status(bucket.getStatus())
                    .build();
            telegramSseEmitterService.broadcastToBot(bot.getId(), eventDto);

            return; // Berhenti. Tidak diproses ke AI atau CS
        }

        // 6. Validasi Penolakan File untuk AI_HANDLING:
        // Jika file berukuran >10MB atau formatnya bukan foto (jpg/jpeg/png/webp), PDF (.pdf), atau MP4 (.mp4):
        // Tolak jika sedang dihandle oleh AI. Jika dihandle oleh agen manusia, biarkan saja (tidak ditolak otomatis).
        if (isFileMessage && (isOversized || !isValidFormat) && "AI_HANDLING".equalsIgnoreCase(bucket.getStatus())) {
            String rejectionText = isOversized
                    ? "File ditolak karena terlalu besar (batas maksimal 10 mb)"
                    : "File ditolak karena format nya tidak valid";

            log.warn("Rejected inbound file from chatId={} on bucket={}: oversized={}, validFormat={}, reason='{}'",
                    telegramChatId, bucket.getId(), isOversized, isValidFormat, rejectionText);

            // Perpanjang/reset timer debounce Redis (5s) tanpa memasukkan pesan ke buffer
            telegramAiBufferService.resetTimerOnly(bucket.getId());

            // Kirim pesan balasan penolakan ke Telegram Cloud nasabah
            try {
                telegramApiClient.sendMessage(bot.getBotToken(), Map.of(
                        "chat_id", telegramChatId,
                        "text", rejectionText
                ));
            } catch (Exception e) {
                log.error("Failed to send rejection to Telegram customer: {}", e.getMessage());
            }

            // Simpan pesan penolakan AI ke database & broadcast SSE
            TelegramMessageEntity rejectionEntity = TelegramMessageEntity.builder()
                    .botId(bot.getId())
                    .tenantId(bot.getTenantId())
                    .bucketId(bucket.getId())
                    .telegramChatId(telegramChatId)
                    .senderType("AI")
                    .senderName("AVA")
                    .messageText(rejectionText)
                    .mediaType("TEXT")
                    .timestamp(new Date())
                    .build();
            TelegramMessageEntity savedRejection = telegramMessageDataService.save(rejectionEntity);

            ResTelegramChatEventDto eventDto = ResTelegramChatEventDto.builder()
                    .eventType("NEW_MESSAGE")
                    .botId(bot.getId())
                    .bucketId(bucket.getId())
                    .telegramChatId(telegramChatId)
                    .messageId(savedRejection != null ? savedRejection.getId() : null)
                    .senderType("AI")
                    .senderName("AVA")
                    .messageText(rejectionText)
                    .mediaType("TEXT")
                    .timestamp(new Date())
                    .status(bucket.getStatus())
                    .build();
            telegramSseEmitterService.broadcastToBot(bot.getId(), eventDto);

            return; // Berhenti. Bubble chat yang mengandung file yang ditolak tidak masuk ke Redis buffer
        }

        // 6. Unduh Media dari Telegram & Simpan ke MinIO (Maks. 10MB)
        String mediaUrl = null;
        Long actualFileSize = fileSize;
        if (fileId != null) {
            try {
                byte[] fileBytes = telegramMediaService.downloadTelegramFile(bot.getBotToken(), fileId);
                if (fileBytes != null && fileBytes.length > 0) {
                    // Validasi Magic Bytes via Apache Tika untuk mencegah extension spoofing
                    if (!telegramMediaService.isValidMediaBytes(fileBytes, fileName)) {
                        log.warn("Security Alert: Inbound Telegram file rejected by Apache Tika (magic bytes mismatch / malicious binary) from chatId={}", telegramChatId);
                        try {
                            telegramApiClient.sendMessage(bot.getBotToken(), java.util.Map.of(
                                    "chat_id", telegramChatId,
                                    "text", "File ditolak karena format nya tidak valid"
                            ));
                        } catch (Exception ignored) {}
                        return;
                    }

                    // Kompresi Cerdas Inbound (Foto maks 1280px/JPEG 80%, PDF FlateDecode Lossless, MP4 FastStart)
                    byte[] compressedBytes = MediaCompressionUtil.compressMedia(fileBytes, mediaType);
                    actualFileSize = (long) compressedBytes.length;

                    String sanitizedFilename = (fileName != null ? fileName : "file").replaceAll("[^a-zA-Z0-9._-]", "_");
                    String objectName = bot.getTenantId() + "/" + bucket.getId() + "/" + System.currentTimeMillis() + "_" + sanitizedFilename;
                    minioStorageService.uploadFile(objectName, compressedBytes, mimeType);
                    mediaUrl = minioStorageService.buildStreamUrl(objectName);
                    log.info("Successfully compressed ({} -> {} bytes) and uploaded Telegram media to MinIO: {}",
                            fileBytes.length, actualFileSize, mediaUrl);
                }
            } catch (Exception e) {
                log.error("Failed to download/compress/upload Telegram media for fileId={}: {}", fileId, e.getMessage(), e);
            }
        }

        // Penanganan Foto + Caption (Keduanya wajib diambil)
        String messageContent;
        if (!"TEXT".equals(mediaType)) {
            // Jika ada caption, simpan caption ke messageText. Jika tidak ada caption, messageText kosong/null
            messageContent = StringUtils.hasText(caption) ? caption.trim() : null;
        } else {
            messageContent = StringUtils.hasText(text) ? text.trim() : "";
        }

        // Ekstraksi Reply / Quote jika nasabah me-reply bubble chat sebelumnya
        Long telegramMessageId = message.getMessageId();
        String mediaGroupId = message.getMediaGroupId();
        Long replyToTelegramMessageId = null;
        String replyToMessageId = null;
        String replyToSenderName = null;
        String replyToText = null;

        if (message.getReplyToMessage() != null) {
            TelegramUpdateDto.MessageDto replyTo = message.getReplyToMessage();
            replyToTelegramMessageId = replyTo.getMessageId();
            if (replyTo.getFrom() != null && StringUtils.hasText(replyTo.getFrom().getFirstName())) {
                replyToSenderName = replyTo.getFrom().getFirstName();
            } else {
                replyToSenderName = "Pengirim";
            }
            replyToText = StringUtils.hasText(replyTo.getText()) ? replyTo.getText() : replyTo.getCaption();
            if (!StringUtils.hasText(replyToText)) {
                if (replyTo.getPhoto() != null && !replyTo.getPhoto().isEmpty()) replyToText = "[Foto]";
                else if (replyTo.getDocument() != null) replyToText = "[Dokumen: " + (replyTo.getDocument().getFileName() != null ? replyTo.getDocument().getFileName() : "file") + "]";
                else if (replyTo.getVideo() != null) replyToText = "[Video]";
            }

            // Cari dokumen asal di database MongoDB jika ada
            if (replyToTelegramMessageId != null) {
                Optional<TelegramMessageEntity> originOpt = telegramMessageDataService.findByTelegramChatIdAndTelegramMessageId(telegramChatId, replyToTelegramMessageId);
                if (originOpt.isPresent()) {
                    TelegramMessageEntity origin = originOpt.get();
                    replyToMessageId = origin.getId();
                    if (StringUtils.hasText(origin.getSenderName())) replyToSenderName = origin.getSenderName();
                    if (StringUtils.hasText(origin.getMessageText())) replyToText = origin.getMessageText();
                }
            }
        }

        // 6. Simpan Pesan ke Riwayat Percakapan (MongoDB)
        TelegramMessageEntity messageEntity = TelegramMessageEntity.builder()
                .botId(bot.getId())
                .tenantId(bot.getTenantId())
                .bucketId(bucket.getId())
                .telegramChatId(telegramChatId)
                .telegramMessageId(telegramMessageId)
                .replyToMessageId(replyToMessageId)
                .replyToTelegramMessageId(replyToTelegramMessageId)
                .replyToSenderName(replyToSenderName)
                .replyToText(replyToText)
                .mediaGroupId(mediaGroupId)
                .senderType("CUSTOMER")
                .senderName(customerFirstName)
                .messageText(messageContent)
                .mediaType(mediaType)
                .mediaUrl(mediaUrl)
                .fileName(fileName)
                .fileSize(actualFileSize)
                .timestamp(new Date())
                .build();

        TelegramMessageEntity savedMsg = telegramMessageDataService.save(messageEntity);
        log.info("Inbound customer message saved: bucketId={}, mediaType={}, text='{}', replyTo={}", bucket.getId(), mediaType, messageContent, replyToTelegramMessageId);

        // 7. Broadcast event pesan baru ke CS via SSE
        ResTelegramChatEventDto eventDto = ResTelegramChatEventDto.builder()
                .eventType("NEW_MESSAGE")
                .botId(bot.getId())
                .bucketId(bucket.getId())
                .telegramChatId(telegramChatId)
                .messageId(savedMsg != null ? savedMsg.getId() : null)
                .telegramMessageId(telegramMessageId)
                .replyToMessageId(replyToMessageId)
                .replyToTelegramMessageId(replyToTelegramMessageId)
                .replyToSenderName(replyToSenderName)
                .replyToText(replyToText)
                .mediaGroupId(mediaGroupId)
                .senderType("CUSTOMER")
                .senderName(customerFirstName)
                .messageText(messageContent)
                .mediaType(mediaType)
                .mediaUrl(mediaUrl)
                .fileName(fileName)
                .fileSize(actualFileSize)
                .timestamp(savedMsg != null && savedMsg.getTimestamp() != null ? savedMsg.getTimestamp() : new Date())
                .status(bucket.getStatus())
                .build();
        telegramSseEmitterService.broadcastToBot(bot.getId(), eventDto);

        // 8. Handle Redis Debounce Buffer (5s)
        // Dijalankan HANYA SETELAH dokumen/media berhasil diunduh dan disimpan ke MinIO/Mongo!
        // Sehingga timer 5s countdown dimulai tepat setelah upload selesai.
        if ("AI_HANDLING".equalsIgnoreCase(bucket.getStatus())) {
            String textForAi = null;
            if (mediaUrl != null) {
                if (StringUtils.hasText(messageContent)) {
                    textForAi = messageContent + " (Lampiran: " + (fileName != null ? fileName : mediaType.toLowerCase()) + ")";
                } else {
                    textForAi = "[Nasabah melampirkan " + mediaType.toLowerCase() + ": " + (fileName != null ? fileName : "file") + "]";
                }
            } else if (StringUtils.hasText(messageContent)) {
                textForAi = messageContent;
            }

            // Jika ini merupakan pesan reply, sertakan konteks yang di-reply ke prompt AI
            if (StringUtils.hasText(replyToText)) {
                textForAi = "[Membalas pesan dari " + replyToSenderName + ": \"" + replyToText + "\"]\n" + (textForAi != null ? textForAi : "");
            }

            if (StringUtils.hasText(textForAi)) {
                telegramAiBufferService.appendMessageAndResetTimer(bucket.getId(), textForAi.trim());
            }
        }
    }
}
