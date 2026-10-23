package com.techno.berijalan.telegramservice.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqUpdateTelegramBotDto {

    @NotBlank(message = "ID bot wajib diisi!")
    private String id;

    private String tenantId;

    private String botName;

    private String botToken;

    private Boolean isActive;

    private String photoBase64;
}
