package com.techno.berijalan.telegramservice.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResVerifyTelegramBotDto {

    private String botUsername;
    private String telegramName;
    private Boolean isValid;
    private Boolean isAlreadyRegistered;
    private String registeredTenantId;
    private Boolean isCurrentlyActive;
}
