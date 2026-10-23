package com.techno.berijalan.telegramservice.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("telegram_message")
public class TelegramMessageEntity {

    @Id
    private String id;

    private String botId;

    private String tenantId;

    private String bucketId;           // Terhubung ke tiket mana (bisa null saat masih di-handle AI)

    private Long telegramChatId;

    private Long telegramMessageId;

    // Relasi Reply / Quote
    private String replyToMessageId;           // ID dokumen pesan MongoDB yang di-reply
    private Long replyToTelegramMessageId;     // ID pesan asli Telegram yang di-reply
    private String replyToSenderName;          // Nama pengirim yang di-reply
    private String replyToText;                // Kutipan ringkas pesan yang di-reply

    // Media Group (Album / Multi Media dalam 1 pengiriman)
    private String mediaGroupId;

    // "CUSTOMER", "AI", "AGENT"
    private String senderType;

    private String senderName;

    private String messageText;

    private String mediaUrl;           // URL file MinIO atau Base64

    // "TEXT", "IMAGE", "DOCUMENT", "VIDEO"
    private String mediaType;

    private String fileName;

    private Long fileSize;

    private Date timestamp;
}
