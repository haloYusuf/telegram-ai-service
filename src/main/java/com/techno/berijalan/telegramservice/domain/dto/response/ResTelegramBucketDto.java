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
public class ResTelegramBucketDto {

    private String id;
    private String botId;
    private String tenantId;
    private Long telegramChatId;
    private String customerFirstName;
    private String customerUsername;
    private String customerPhoneNumber;
    private String status;           // "WAITING_AGENT", "ON_GOING", "RESOLVED"
    private String agentId;
    private String agentName;
    private String lastMessageText;  // Cuplikan preview pesan terakhir untuk UI list
    private Date lastMessageTimestamp;
    private Date createdAt;
    private Date updatedAt;
}
