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
public class ResTelegramBotDto {

    private String id;
    private String tenantId;
    private String botName;
    private String botUsername;
    private String botToken;
    private String maskedToken;
    private Boolean isActive;
    private String photoUrl;
    private String webhookUrl;
    private Date createdAt;
    private Date updatedAt;
}
