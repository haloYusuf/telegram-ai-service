package com.techno.berijalan.telegramservice.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResTelegramChatEventDto {

    private String eventType;       // "NEW_MESSAGE", "BUCKET_RESOLVED", "BUCKET_REOPENED", "AI_HANDOVER"
    private String botId;
    private String bucketId;
    private Long telegramChatId;
    private String messageId;
    private String senderType;      // "CUSTOMER", "AI", "AGENT", "SYSTEM"
    private String senderName;
    private String messageText;
    private String mediaUrl;
    private String mediaType;
    private String fileName;
    private Long fileSize;
    private Date timestamp;
    private String status;          // "WAITING_AGENT", "ON_GOING", "RESOLVED", "AI_HANDLING"

    // Telegram & Reply Info
    private Long telegramMessageId;
    private String replyToMessageId;
    private Long replyToTelegramMessageId;
    private String replyToSenderName;
    private String replyToText;

    // Media Group (Album / Multi Media dalam 1 bubble)
    private String mediaGroupId;
}
