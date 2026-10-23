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
public class ResTelegramChatMessageDto {

    private String messageId;
    private String bucketId;
    private String senderType; // "CUSTOMER", "AI", atau "AGENT"
    private String senderName;
    private String messageText;
    private String mediaUrl;
    private String mediaType;
    private String fileName;
    private Long fileSize;
    private Date timestamp;

    // Telegram & Reply Info
    private Long telegramMessageId;
    private String replyToMessageId;
    private Long replyToTelegramMessageId;
    private String replyToSenderName;
    private String replyToText;

    // Media Group (Album / Multi Media dalam 1 bubble)
    private String mediaGroupId;
}
