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
public class ReqCreateTelegramBotDto {

    private String tenantId;

    private String botName;

    @NotBlank(message = "Bot Token wajib diisi!")
    private String botToken;

    private String photoBase64;
}
