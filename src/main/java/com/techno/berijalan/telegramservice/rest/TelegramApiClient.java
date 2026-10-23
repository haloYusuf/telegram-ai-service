package com.techno.berijalan.telegramservice.rest;

import com.techno.berijalan.telegramservice.config.FeignClientConfig;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramApiResponse;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramFileDto;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramUserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "telegram-api-client",
        url = "${telegram.api-url}",
        configuration = FeignClientConfig.class
)
public interface TelegramApiClient {

    @GetMapping("/bot{botToken}/getMe")
    TelegramApiResponse<TelegramUserDto> getMe(
            @PathVariable("botToken") String botToken
    );

    @PostMapping("/bot{botToken}/setWebhook")
    TelegramApiResponse<Boolean> setWebhook(
            @PathVariable("botToken") String botToken,
            @RequestBody Map<String, Object> requestBody
    );

    @PostMapping("/bot{botToken}/deleteWebhook")
    TelegramApiResponse<Boolean> deleteWebhook(
            @PathVariable("botToken") String botToken
    );

    @PostMapping("/bot{botToken}/setMyName")
    TelegramApiResponse<Boolean> setMyName(
            @PathVariable("botToken") String botToken,
            @RequestBody Map<String, Object> requestBody
    );

    @PostMapping("/bot{botToken}/sendMessage")
    TelegramApiResponse<Object> sendMessage(
            @PathVariable("botToken") String botToken,
            @RequestBody Map<String, Object> requestBody
    );

    @GetMapping("/bot{botToken}/getFile")
    TelegramApiResponse<TelegramFileDto> getFile(
            @PathVariable("botToken") String botToken,
            @RequestParam("file_id") String fileId
    );
}
