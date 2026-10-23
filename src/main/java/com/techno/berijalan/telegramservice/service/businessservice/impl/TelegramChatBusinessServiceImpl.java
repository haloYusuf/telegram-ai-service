package com.techno.berijalan.telegramservice.service.businessservice.impl;

import com.techno.berijalan.telegramservice.domain.dto.request.ReqAttachmentDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqSendTelegramMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResSendTelegramMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramBucketDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatEventDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramApiResponse;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;
import com.techno.berijalan.telegramservice.rest.TelegramApiClient;
import com.techno.berijalan.telegramservice.service.businessservice.MinioStorageService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramChatBusinessService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramMediaService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramSseEmitterService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBotDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBucketDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramMessageDataService;
import com.techno.berijalan.telegramservice.util.MediaCompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramChatBusinessServiceImpl implements TelegramChatBusinessService {

    private final TelegramBucketDataService telegramBucketDataService;
    private final TelegramBotDataService telegramBotDataService;
    private final TelegramMessageDataService telegramMessageDataService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramSseEmitterService telegramSseEmitterService;
    private final TelegramMediaService telegramMediaService;
    private final MinioStorageService minioStorageService;

    @Override
    public ResSendTelegramMessageDto sendMessage(ReqSendTelegramMessageDto requestDto) {
        if (requestDto == null || !StringUtils.hasText(requestDto.getBucketId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket ID wajib diisi!");
        }

        boolean hasMedia = StringUtils.hasText(requestDto.getMediaType()) &&
                (StringUtils.hasText(requestDto.getMediaBase64()) || StringUtils.hasText(requestDto.getMediaUrl()));

        if (!hasMedia && !StringUtils.hasText(requestDto.getMessageText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pesan balasan atau media wajib diisi!");
        }

        // 1. Ambil tiket / room chat nasabah
        TelegramBucketEntity bucket = telegramBucketDataService.findById(requestDto.getBucketId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tiket/Bucket Telegram dengan ID " + requestDto.getBucketId() + " tidak ditemukan!"));

        // Intercept 2-Lapis (BE): jika tiket sudah berstatus RESOLVED, tolak pengiriman pesan
        if ("RESOLVED".equalsIgnoreCase(bucket.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tiket percakapan ini sudah selesai (RESOLVED). Buka kembali (re-open) tiket terlebih dahulu sebelum mengirim pesan!");
        }

        // Intercept 2-Lapis (BE): jika tiket sedang ditangani oleh AI, tolak intervensi agen
        if ("AI_HANDLING".equalsIgnoreCase(bucket.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sesi percakapan saat ini sedang ditangani oleh AI Gemini. Agen belum dapat mengirim pesan!");
        }

        // 2. Ambil token bot pemilik tiket (Multi-Bot Isolation: pasti membalas dari bot yang bersangkutan)
        TelegramBotEntity bot = telegramBotDataService.findById(bucket.getBotId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot Telegram pemilik tiket tidak ditemukan!"));

        if (!Boolean.TRUE.equals(bot.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot Telegram pemilik tiket sedang tidak aktif!");
        }

        // Resolusi informasi pesan yang di-reply (jika agen me-reply salah satu chat)
        Long replyToTelegramMessageId = null;
        String replyToMessageId = null;
        String replyToSenderName = null;
        String replyToText = null;

        if (StringUtils.hasText(requestDto.getReplyToMessageId())) {
            Optional<TelegramMessageEntity> replyTargetOpt = telegramMessageDataService.findById(requestDto.getReplyToMessageId().trim());
            if (replyTargetOpt.isPresent()) {
                TelegramMessageEntity target = replyTargetOpt.get();
                replyToMessageId = target.getId();
                replyToTelegramMessageId = target.getTelegramMessageId();
                replyToSenderName = target.getSenderName();
                replyToText = target.getMessageText();
                if (!StringUtils.hasText(replyToText)) {
                    replyToText = "[" + (target.getMediaType() != null ? target.getMediaType() : "Lampiran") + "]";
                }

                // Fallback untuk pesan lama yang belum memiliki telegramMessageId tersimpan di DB
                if (replyToTelegramMessageId == null && bucket.getTelegramChatId() != null) {
                    List<TelegramMessageEntity> history = telegramMessageDataService.findByBucketIdOrderByTimestampAsc(bucket.getId());
                    for (int i = history.size() - 1; i >= 0; i--) {
                        if (history.get(i).getTelegramMessageId() != null) {
                            replyToTelegramMessageId = history.get(i).getTelegramMessageId();
                            break;
                        }
                    }
                }
            }
        }

        String agentName = StringUtils.hasText(requestDto.getAgentName()) ? requestDto.getAgentName().trim() : "Agent 1";
        String agentId = StringUtils.hasText(requestDto.getAgentId()) ? requestDto.getAgentId().trim() : "agent-1";

        // Penanganan Multi-Attachment (> 1 dokumen / foto dalam 1 kali kirim)
        if (requestDto.getAttachments() != null && requestDto.getAttachments().size() > 1) {
            String mediaGroupId = "grp_" + System.currentTimeMillis();
            TelegramMessageEntity lastSaved = null;

            for (int i = 0; i < requestDto.getAttachments().size(); i++) {
                ReqAttachmentDto att = requestDto.getAttachments().get(i);
                String itemCaption = (i == 0 && StringUtils.hasText(requestDto.getMessageText())) ? requestDto.getMessageText().trim() : null;
                String itemFileName = StringUtils.hasText(att.getFileName()) ? att.getFileName().trim() : ("file_" + i + "_" + System.currentTimeMillis());
                String itemType = (att.getMediaType() != null) ? att.getMediaType().toUpperCase() : "DOCUMENT";
                if ("PHOTO".equals(itemType)) itemType = "IMAGE";

                byte[] fileBytes;
                String itemMediaUrl = null;
                Long itemFileSize = att.getFileSize();

                if (StringUtils.hasText(att.getMediaBase64())) {
                    String base64Str = att.getMediaBase64();
                    if (base64Str.contains(",")) base64Str = base64Str.substring(base64Str.indexOf(",") + 1);
                    fileBytes = Base64.getDecoder().decode(base64Str); // Berkas Asli Uncompressed untuk Telegram
                    byte[] compressedBytes = MediaCompressionUtil.compressMedia(fileBytes, itemType); // Berkas Kompresi untuk MinIO
                    itemFileSize = (long) compressedBytes.length;

                    String sanitizedFilename = itemFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String objectName = bot.getTenantId() + "/" + bucket.getId() + "/" + System.currentTimeMillis() + "_" + sanitizedFilename;
                    minioStorageService.uploadFile(objectName, compressedBytes, minioStorageService.determineContentType(itemFileName));
                    itemMediaUrl = minioStorageService.buildStreamUrl(objectName);
                } else if (StringUtils.hasText(att.getMediaUrl())) {
                    itemMediaUrl = att.getMediaUrl();
                    String rawLink = att.getMediaUrl().trim();
                    if (rawLink.contains("path=")) {
                        String pathParam = rawLink.substring(rawLink.indexOf("path=") + 5);
                        int amp = pathParam.indexOf("&");
                        if (amp != -1) pathParam = pathParam.substring(0, amp);
                        fileBytes = minioStorageService.downloadFile(URLDecoder.decode(pathParam, StandardCharsets.UTF_8));
                    } else {
                        fileBytes = minioStorageService.downloadFile(rawLink);
                    }
                    if (fileBytes != null) itemFileSize = (long) fileBytes.length;
                } else {
                    continue;
                }

                // Kirim media ke Telegram Cloud
                Long itemTelegramMsgId = null;
                if (replyToTelegramMessageId != null) {
                    itemTelegramMsgId = telegramMediaService.sendMediaToTelegram(bot.getBotToken(), bucket.getTelegramChatId(), itemType, fileBytes, itemFileName, itemCaption, replyToTelegramMessageId);
                } else {
                    itemTelegramMsgId = telegramMediaService.sendMediaToTelegram(bot.getBotToken(), bucket.getTelegramChatId(), itemType, fileBytes, itemFileName, itemCaption);
                }

                // Simpan ke MongoDB
                TelegramMessageEntity entity = TelegramMessageEntity.builder()
                        .botId(bot.getId())
                        .tenantId(bot.getTenantId())
                        .bucketId(bucket.getId())
                        .telegramChatId(bucket.getTelegramChatId())
                        .telegramMessageId(itemTelegramMsgId)
                        .replyToMessageId(replyToMessageId)
                        .replyToTelegramMessageId(replyToTelegramMessageId)
                        .replyToSenderName(replyToSenderName)
                        .replyToText(replyToText)
                        .mediaGroupId(mediaGroupId)
                        .senderType("AGENT")
                        .senderName(agentName)
                        .messageText(itemCaption)
                        .mediaType(itemType)
                        .mediaUrl(itemMediaUrl)
                        .fileName(itemFileName)
                        .fileSize(itemFileSize)
                        .timestamp(new Date())
                        .build();

                lastSaved = telegramMessageDataService.save(entity);

                // Broadcast SSE per item
                ResTelegramChatEventDto itemEvent = ResTelegramChatEventDto.builder()
                        .eventType("NEW_MESSAGE")
                        .botId(bot.getId())
                        .bucketId(bucket.getId())
                        .telegramChatId(bucket.getTelegramChatId())
                        .messageId(lastSaved.getId())
                        .telegramMessageId(itemTelegramMsgId)
                        .replyToMessageId(replyToMessageId)
                        .replyToTelegramMessageId(replyToTelegramMessageId)
                        .replyToSenderName(replyToSenderName)
                        .replyToText(replyToText)
                        .mediaGroupId(mediaGroupId)
                        .senderType("AGENT")
                        .senderName(agentName)
                        .messageText(itemCaption)
                        .mediaType(itemType)
                        .mediaUrl(itemMediaUrl)
                        .fileName(itemFileName)
                        .fileSize(itemFileSize)
                        .timestamp(lastSaved.getTimestamp())
                        .status(bucket.getStatus())
                        .build();
                telegramSseEmitterService.broadcastToBot(bot.getId(), itemEvent);
            }

            // Update bucket
            if ("WAITING_AGENT".equals(bucket.getStatus())) {
                bucket.setStatus("ON_GOING");
            }
            bucket.setAgentId(agentId);
            bucket.setAgentName(agentName);
            bucket.setUpdatedAt(new Date());
            telegramBucketDataService.save(bucket);

            return ResSendTelegramMessageDto.builder()
                    .messageId(lastSaved != null ? lastSaved.getId() : null)
                    .bucketId(bucket.getId())
                    .botId(bot.getId())
                    .telegramChatId(bucket.getTelegramChatId())
                    .senderType("AGENT")
                    .senderName(agentName)
                    .messageText(requestDto.getMessageText())
                    .mediaType("DOCUMENT")
                    .timestamp(new Date())
                    .build();
        }

        String mediaType = "TEXT";
        String mediaUrl = null;
        String fileName = null;
        Long fileSize = null;
        String caption = StringUtils.hasText(requestDto.getMessageText()) ? requestDto.getMessageText().trim() : null;

        Long sentTelegramMessageId = null;

        // 3. Proses Pengiriman Single Media atau Pesan Teks
        if (hasMedia) {
            String rawType = requestDto.getMediaType().toUpperCase();
            if (!"IMAGE".equals(rawType) && !"PHOTO".equals(rawType) && !"DOCUMENT".equals(rawType) && !"VIDEO".equals(rawType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format media tidak didukung! Hanya dokumen, foto, dan video yang diperbolehkan.");
            }
            mediaType = "PHOTO".equals(rawType) ? "IMAGE" : rawType;

            byte[] fileBytes;
            fileName = StringUtils.hasText(requestDto.getFileName()) ? requestDto.getFileName().trim() : ("file_" + System.currentTimeMillis());

            if (StringUtils.hasText(requestDto.getMediaBase64())) {
                // Transmisi <= 5MB menggunakan Base64
                try {
                    String base64Str = requestDto.getMediaBase64();
                    if (base64Str.contains(",")) {
                        base64Str = base64Str.substring(base64Str.indexOf(",") + 1);
                    }
                    fileBytes = Base64.getDecoder().decode(base64Str); // Berkas Asli Uncompressed untuk Telegram
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format Base64 media tidak valid: " + e.getMessage());
                }

                if (fileBytes.length > TelegramMediaService.MAX_FILE_SIZE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ukuran file melebihi batas maksimal 10MB!");
                }
                if (fileBytes.length > TelegramMediaService.BASE64_MAX_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media dengan ukuran di atas 5MB wajib menggunakan upload link (URL)!");
                }

                // Kompresi Berkas untuk MinIO Internal
                byte[] compressedBytes = MediaCompressionUtil.compressMedia(fileBytes, mediaType);

                // Simpan ke MinIO (Berkas Terkompresi - Hemat Storage MinIO)
                String sanitizedFilename = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                String objectName = bot.getTenantId() + "/" + bucket.getId() + "/" + System.currentTimeMillis() + "_" + sanitizedFilename;
                minioStorageService.uploadFile(objectName, compressedBytes, minioStorageService.determineContentType(fileName));
                mediaUrl = minioStorageService.buildStreamUrl(objectName);
                fileSize = (long) compressedBytes.length;

                // Kirim ke Telegram Cloud versi ASLI Uncompressed (Kualitas Asli untuk Nasabah)

                // Kirim ke Telegram Cloud via Multipart (dengan reply_parameters jika ada)
                if (replyToTelegramMessageId != null) {
                    sentTelegramMessageId = telegramMediaService.sendMediaToTelegram(bot.getBotToken(), bucket.getTelegramChatId(), mediaType, fileBytes, fileName, caption, replyToTelegramMessageId);
                } else {
                    sentTelegramMessageId = telegramMediaService.sendMediaToTelegram(bot.getBotToken(), bucket.getTelegramChatId(), mediaType, fileBytes, fileName, caption);
                }
            } else {
                // Transmisi > 5MB sampai 10MB menggunakan link / URL
                String rawLink = requestDto.getMediaUrl().trim();
                try {
                    if (rawLink.contains("path=")) {
                        String pathParam = rawLink.substring(rawLink.indexOf("path=") + 5);
                        int amp = pathParam.indexOf("&");
                        if (amp != -1) pathParam = pathParam.substring(0, amp);
                        String decoded = URLDecoder.decode(pathParam, StandardCharsets.UTF_8);
                        fileBytes = minioStorageService.downloadFile(decoded);
                        mediaUrl = rawLink;
                    } else if (!rawLink.startsWith("http://") && !rawLink.startsWith("https://")) {
                        fileBytes = minioStorageService.downloadFile(rawLink);
                        mediaUrl = minioStorageService.buildStreamUrl(rawLink);
                    } else {
                        fileBytes = new URL(rawLink).openStream().readAllBytes();
                        String sanitizedFilename = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                        String objectName = bot.getTenantId() + "/" + bucket.getId() + "/" + System.currentTimeMillis() + "_" + sanitizedFilename;
                        minioStorageService.uploadFile(objectName, fileBytes, minioStorageService.determineContentType(sanitizedFilename));
                        mediaUrl = minioStorageService.buildStreamUrl(objectName);
                    }
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal memproses media dari URL: " + e.getMessage());
                }

                if (fileBytes.length > TelegramMediaService.MAX_FILE_SIZE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ukuran file melebihi batas maksimal 10MB!");
                }
                fileSize = (long) fileBytes.length;

                // Kirim ke Telegram Cloud via Multipart (dengan reply_parameters jika ada)
                if (replyToTelegramMessageId != null) {
                    sentTelegramMessageId = telegramMediaService.sendMediaToTelegram(bot.getBotToken(), bucket.getTelegramChatId(), mediaType, fileBytes, fileName, caption, replyToTelegramMessageId);
                } else {
                    sentTelegramMessageId = telegramMediaService.sendMediaToTelegram(bot.getBotToken(), bucket.getTelegramChatId(), mediaType, fileBytes, fileName, caption);
                }
            }
        } else {
            // Pengiriman Teks Biasa
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", bucket.getTelegramChatId());
            body.put("text", caption);

            if (replyToTelegramMessageId != null) {
                Map<String, Object> replyParams = new HashMap<>();
                replyParams.put("message_id", replyToTelegramMessageId);
                replyParams.put("allow_sending_without_reply", true);
                body.put("reply_parameters", replyParams);
                body.put("reply_to_message_id", replyToTelegramMessageId);
            }

            try {
                TelegramApiResponse<Object> apiRes = telegramApiClient.sendMessage(bot.getBotToken(), body);
                if (apiRes != null && apiRes.getResult() instanceof Map<?, ?> resMap) {
                    Object mid = resMap.get("message_id");
                    if (mid instanceof Number num) {
                        sentTelegramMessageId = num.longValue();
                    }
                }
                log.info("Dispatched outbound text message to Telegram Cloud: chatId={}, botId={}, replyTo={}, sentMsgId={}",
                        bucket.getTelegramChatId(), bot.getId(), replyToTelegramMessageId, sentTelegramMessageId);
            } catch (Exception e) {
                log.error("Failed to send message to Telegram Cloud: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal mengirim pesan ke Telegram Cloud: " + e.getMessage());
            }
        }

        // 4. Simpan rekaman pesan ke database dengan senderType = AGENT
        TelegramMessageEntity messageEntity = TelegramMessageEntity.builder()
                .botId(bot.getId())
                .tenantId(bot.getTenantId())
                .bucketId(bucket.getId())
                .telegramChatId(bucket.getTelegramChatId())
                .telegramMessageId(sentTelegramMessageId)
                .replyToMessageId(replyToMessageId)
                .replyToTelegramMessageId(replyToTelegramMessageId)
                .replyToSenderName(replyToSenderName)
                .replyToText(replyToText)
                .senderType("AGENT")
                .senderName(agentName)
                .messageText(caption)
                .mediaType(mediaType)
                .mediaUrl(mediaUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .timestamp(new Date())
                .build();

        TelegramMessageEntity savedMessage = telegramMessageDataService.save(messageEntity);

        // 5. Update status tiket bucket jika sebelumnya WAITING_AGENT -> ON_GOING
        if ("WAITING_AGENT".equals(bucket.getStatus())) {
            bucket.setStatus("ON_GOING");
        }
        bucket.setAgentId(agentId);
        bucket.setAgentName(agentName);
        bucket.setUpdatedAt(new Date());
        telegramBucketDataService.save(bucket);

        // 6. Broadcast event pesan outbound CS via SSE
        ResTelegramChatEventDto eventDto = ResTelegramChatEventDto.builder()
                .eventType("NEW_MESSAGE")
                .botId(bot.getId())
                .bucketId(bucket.getId())
                .telegramChatId(bucket.getTelegramChatId())
                .messageId(savedMessage.getId())
                .telegramMessageId(sentTelegramMessageId)
                .replyToMessageId(replyToMessageId)
                .replyToTelegramMessageId(replyToTelegramMessageId)
                .replyToSenderName(replyToSenderName)
                .replyToText(replyToText)
                .senderType("AGENT")
                .senderName(agentName)
                .messageText(savedMessage.getMessageText())
                .mediaType(mediaType)
                .mediaUrl(mediaUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .timestamp(savedMessage.getTimestamp())
                .status(bucket.getStatus())
                .build();
        telegramSseEmitterService.broadcastToBot(bot.getId(), eventDto);

        return ResSendTelegramMessageDto.builder()
                .messageId(savedMessage.getId())
                .bucketId(bucket.getId())
                .botId(bot.getId())
                .telegramChatId(bucket.getTelegramChatId())
                .senderType("AGENT")
                .senderName(agentName)
                .messageText(savedMessage.getMessageText())
                .mediaType(mediaType)
                .mediaUrl(mediaUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .timestamp(savedMessage.getTimestamp())
                .build();
    }

    @Override
    public List<ResTelegramChatMessageDto> getChatHistory(String bucketId) {
        if (!StringUtils.hasText(bucketId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket ID wajib diisi!");
        }

        TelegramBucketEntity bucket = telegramBucketDataService.findById(bucketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tiket/Bucket Telegram dengan ID " + bucketId + " tidak ditemukan!"));

        String currentCustomerName = StringUtils.hasText(bucket.getCustomerFirstName()) ? bucket.getCustomerFirstName() : "Customer";

        List<TelegramMessageEntity> messages = telegramMessageDataService.findByBucketIdOrderByTimestampAsc(bucketId);

        return messages.stream().map(msg -> {
            String displayName = "CUSTOMER".equals(msg.getSenderType()) ? currentCustomerName : msg.getSenderName();

            return ResTelegramChatMessageDto.builder()
                    .messageId(msg.getId())
                    .bucketId(msg.getBucketId())
                    .senderType(msg.getSenderType())
                    .senderName(displayName)
                    .messageText(msg.getMessageText())
                    .mediaUrl(msg.getMediaUrl())
                    .mediaType(msg.getMediaType())
                    .fileName(msg.getFileName())
                    .fileSize(msg.getFileSize())
                    .timestamp(msg.getTimestamp())
                    .telegramMessageId(msg.getTelegramMessageId())
                    .replyToMessageId(msg.getReplyToMessageId())
                    .replyToTelegramMessageId(msg.getReplyToTelegramMessageId())
                    .replyToSenderName(msg.getReplyToSenderName())
                    .replyToText(msg.getReplyToText())
                    .mediaGroupId(msg.getMediaGroupId())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<ResTelegramBucketDto> getBucketList(String botId, String status) {
        List<TelegramBucketEntity> buckets;

        String normalizedStatus = normalizeStatus(status);
        boolean hasBot = StringUtils.hasText(botId);
        boolean hasStatus = StringUtils.hasText(normalizedStatus);

        if (hasBot && hasStatus) {
            buckets = telegramBucketDataService.findByBotIdAndStatusOrderByUpdatedAtDesc(botId.trim(), normalizedStatus);
        } else if (hasBot) {
            buckets = telegramBucketDataService.findByBotIdOrderByUpdatedAtDesc(botId.trim());
        } else if (hasStatus) {
            buckets = telegramBucketDataService.findByStatusOrderByUpdatedAtDesc(normalizedStatus);
        } else {
            buckets = telegramBucketDataService.findAllByOrderByUpdatedAtDesc();
        }

        return buckets.stream().map(bucket -> {
            Optional<TelegramMessageEntity> latestMsgOpt = telegramMessageDataService.findLatestMessageByBucketId(bucket.getId());
            String lastMessageText = latestMsgOpt.map(m -> {
                if (StringUtils.hasText(m.getMessageText())) return m.getMessageText();
                if ("IMAGE".equalsIgnoreCase(m.getMediaType())) return "[Foto]";
                if ("VIDEO".equalsIgnoreCase(m.getMediaType())) return "[Video]";
                if ("DOCUMENT".equalsIgnoreCase(m.getMediaType())) return "[Dokumen: " + (m.getFileName() != null ? m.getFileName() : "file") + "]";
                return null;
            }).orElse(null);
            Date lastMessageTimestamp = latestMsgOpt.map(TelegramMessageEntity::getTimestamp).orElse(null);

            return ResTelegramBucketDto.builder()
                    .id(bucket.getId())
                    .botId(bucket.getBotId())
                    .tenantId(bucket.getTenantId())
                    .telegramChatId(bucket.getTelegramChatId())
                    .customerFirstName(bucket.getCustomerFirstName())
                    .customerUsername(bucket.getCustomerUsername())
                    .customerPhoneNumber(bucket.getCustomerPhoneNumber())
                    .status(bucket.getStatus())
                    .agentId(bucket.getAgentId())
                    .agentName(bucket.getAgentName())
                    .lastMessageText(lastMessageText)
                    .lastMessageTimestamp(lastMessageTimestamp)
                    .createdAt(bucket.getCreatedAt())
                    .updatedAt(bucket.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ResTelegramBucketDto toggleBucketStatus(String bucketId) {
        if (!StringUtils.hasText(bucketId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket ID wajib diisi!");
        }

        TelegramBucketEntity bucket = telegramBucketDataService.findById(bucketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tiket/Bucket Telegram dengan ID " + bucketId + " tidak ditemukan!"));

        boolean isCurrentlyResolved = "RESOLVED".equalsIgnoreCase(bucket.getStatus());
        String newStatus = isCurrentlyResolved ? "ON_GOING" : "RESOLVED";
        String eventType = isCurrentlyResolved ? "BUCKET_REOPENED" : "BUCKET_RESOLVED";
        String actionMessage = isCurrentlyResolved
                ? "Tiket percakapan dibuka kembali oleh " + (bucket.getAgentName() != null ? bucket.getAgentName() : "Agent")
                : "Percakapan telah diselesaikan oleh " + (bucket.getAgentName() != null ? bucket.getAgentName() : "Agent");

        bucket.setStatus(newStatus);
        bucket.setUpdatedAt(new Date());
        TelegramBucketEntity saved = telegramBucketDataService.save(bucket);
        log.info("Bucket status toggled: id={}, customer={}, newStatus={}", saved.getId(), saved.getCustomerFirstName(), newStatus);

        // Broadcast event status bucket via SSE agar UI semua CS terupdate secara real-time
        ResTelegramChatEventDto eventDto = ResTelegramChatEventDto.builder()
                .eventType(eventType)
                .botId(saved.getBotId())
                .bucketId(saved.getId())
                .telegramChatId(saved.getTelegramChatId())
                .senderType("AGENT")
                .senderName(saved.getAgentName() != null ? saved.getAgentName() : "Agent")
                .messageText(actionMessage)
                .status(newStatus)
                .timestamp(new Date())
                .build();
        telegramSseEmitterService.broadcastToBot(saved.getBotId(), eventDto);

        Optional<TelegramMessageEntity> latestMsgOpt = telegramMessageDataService.findLatestMessageByBucketId(saved.getId());

        return ResTelegramBucketDto.builder()
                .id(saved.getId())
                .botId(saved.getBotId())
                .tenantId(saved.getTenantId())
                .telegramChatId(saved.getTelegramChatId())
                .customerFirstName(saved.getCustomerFirstName())
                .customerUsername(saved.getCustomerUsername())
                .customerPhoneNumber(saved.getCustomerPhoneNumber())
                .status(saved.getStatus())
                .agentId(saved.getAgentId())
                .agentName(saved.getAgentName())
                .lastMessageText(latestMsgOpt.map(TelegramMessageEntity::getMessageText).orElse(null))
                .lastMessageTimestamp(latestMsgOpt.map(TelegramMessageEntity::getTimestamp).orElse(null))
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String s = status.trim().toUpperCase();
        return switch (s) {
            case "NEW" -> "WAITING_AGENT";
            case "AI" -> "AI_HANDLING";
            case "ONGOING" -> "ON_GOING";
            default -> s;
        };
    }
}
