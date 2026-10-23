package com.techno.berijalan.telegramservice.controller;

import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import com.techno.berijalan.telegramservice.domain.dto.TelegramUpdateDto;
import com.techno.berijalan.telegramservice.domain.dto.response.builder.ResponseBerijalan;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramWebhookBusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Telegram Inbound Webhook", description = "Endpoint penerimaan event dan pesan masuk dari Telegram Cloud")
@RestController
@RequestMapping(PathConstant.ROOT_ENDPOINT_CONTEXT)
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramWebhookBusinessService telegramWebhookBusinessService;

    @Operation(summary = "Penerimaan Webhook Telegram Update", description = "Menerima pesan masuk dari nasabah via Telegram Cloud per Bot ID")
    @PostMapping(PathConstant.URI_TELEGRAM_WEBHOOK)
    public ResponseEntity<Object> handleWebhook(
            @PathVariable String botId,
            @RequestBody TelegramUpdateDto updateDto
    ) {
        log.info("Received incoming Telegram webhook update for botId={}: updateId={}", botId, updateDto != null ? updateDto.getUpdateId() : null);
        telegramWebhookBusinessService.processIncomingUpdate(botId, updateDto);
        return ResponseBerijalan.build("Webhook processed successfully");
    }
}
