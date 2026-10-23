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
public class ResSendTelegramMessageDto {

    private String messageId;
    private String bucketId;
    private String botId;
    private Long telegramChatId;
    private String senderType; // "AGENT"
    private String senderName;
    private String messageText;
    private String mediaUrl;
    private String mediaType;
    private String fileName;
    private Long fileSize;
    private Date timestamp;
}
