package com.techno.berijalan.telegramservice.service.businessservice.impl;

import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqCreateTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqUpdateTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqVerifyTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResVerifyTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramApiResponse;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramUserDto;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;
import com.techno.berijalan.telegramservice.rest.TelegramApiClient;
import com.techno.berijalan.telegramservice.service.businessservice.MinioStorageService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramBotBusinessService;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramMediaService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBotDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotBusinessServiceImpl implements TelegramBotBusinessService {

    private final TelegramBotDataService telegramBotDataService;
    private final TelegramApiClient telegramApiClient;
    private final MinioStorageService minioStorageService;
    private final TelegramMediaService telegramMediaService;

    @Value("${telegram.webhook-base-url}")
    private String webhookBaseUrl;

    @Override
    public ResVerifyTelegramBotDto verifyToken(ReqVerifyTelegramBotDto requestDto) {
        if (requestDto == null || !StringUtils.hasText(requestDto.getBotToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot Token wajib diisi!");
        }

        String rawToken = requestDto.getBotToken().trim();
        TelegramUserDto telegramUser = fetchTelegramUserFromCloud(rawToken);

        Optional<TelegramBotEntity> existingOpt = telegramBotDataService.findFirstByBotToken(rawToken);

        return ResVerifyTelegramBotDto.builder()
                .botUsername(telegramUser.getUsername())
                .telegramName(telegramUser.getFirstName())
                .isValid(true)
                .isAlreadyRegistered(existingOpt.isPresent())
                .registeredTenantId(existingOpt.map(TelegramBotEntity::getTenantId).orElse(null))
                .isCurrentlyActive(existingOpt.map(TelegramBotEntity::getIsActive).orElse(false))
                .build();
    }

    @Override
    public ResTelegramBotDto registerBot(ReqCreateTelegramBotDto requestDto) {
        if (requestDto == null || !StringUtils.hasText(requestDto.getBotToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot Token wajib diisi!");
        }

        String rawToken = requestDto.getBotToken().trim();
        TelegramUserDto telegramUser = fetchTelegramUserFromCloud(rawToken);
        String officialTelegramName = telegramUser.getFirstName();
        String verifiedUsername = telegramUser.getUsername();

        // Cek keberadaan bot di database & validasi Anti-Hijack
        Optional<TelegramBotEntity> existingOpt = telegramBotDataService.findFirstByBotToken(rawToken);
        TelegramBotEntity botEntity;

        if (existingOpt.isPresent()) {
            TelegramBotEntity existingBot = existingOpt.get();
            // tolak jika bot masih aktif di tenant yang berbeda
            if (Boolean.TRUE.equals(existingBot.getIsActive())
                    && StringUtils.hasText(existingBot.getTenantId())
                    && StringUtils.hasText(requestDto.getTenantId())
                    && !existingBot.getTenantId().equals(requestDto.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot sedang aktif digunakan oleh tenant lain!");
            }

            botEntity = existingBot;
            botEntity.setIsActive(true);
            botEntity.setUpdatedAt(new Date());
            if (StringUtils.hasText(requestDto.getTenantId())) {
                botEntity.setTenantId(requestDto.getTenantId());
            }
        } else {
            botEntity = TelegramBotEntity.builder()
                    .tenantId(StringUtils.hasText(requestDto.getTenantId()) ? requestDto.getTenantId() : "tenant-test1")
                    .botToken(rawToken)
                    .isActive(true)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
        }

        botEntity.setBotUsername(verifiedUsername);

        // Logika Penyelarasan Nama Cerdas (setMyName)
        if (StringUtils.hasText(requestDto.getBotName()) && !requestDto.getBotName().trim().equals(officialTelegramName)) {
            // Nama input berbeda dengan nama Telegram -> sinkronkan ke Telegram Cloud
            String customName = requestDto.getBotName().trim();
            log.info("Nama bot berbeda (input='{}', tele='{}'). Menyelaraskan nama ke Telegram Cloud...", customName, officialTelegramName);
            syncNameToTelegramCloud(rawToken, customName);
            botEntity.setBotName(customName);
        } else {
            // Nama sama atau kosong -> gunakan nama Telegram & skip setMyName
            log.info("Nama bot sama atau tidak diisi ('{}'). Skip pemanggilan setMyName.", officialTelegramName);
            botEntity.setBotName(officialTelegramName);
        }

        // Proses upload foto profil bot (MinIO & Telegram Cloud) jika dikirimkan
        handleBotAvatar(botEntity, rawToken, requestDto.getPhotoBase64());

        TelegramBotEntity savedBot = telegramBotDataService.save(botEntity);
        log.info("Telegram bot registered successfully: id={}, username=@{}", savedBot.getId(), savedBot.getBotUsername());

        // Daftarkan Webhook otomatis ke Telegram jika webhookBaseUrl terisi
        String registeredWebhookUrl = setupWebhookForBot(savedBot.getId(), rawToken);

        return mapToDto(savedBot, registeredWebhookUrl);
    }

    @Override
    public ResTelegramBotDto updateBot(ReqUpdateTelegramBotDto requestDto) {
        if (requestDto == null || !StringUtils.hasText(requestDto.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID bot wajib diisi!");
        }

        TelegramBotEntity bot = telegramBotDataService.findById(requestDto.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot Telegram dengan ID " + requestDto.getId() + " tidak ditemukan!"));

        // Jika token bot diganti
        if (StringUtils.hasText(requestDto.getBotToken()) && !requestDto.getBotToken().trim().equals(bot.getBotToken())) {
            String newToken = requestDto.getBotToken().trim();
            String oldToken = bot.getBotToken();

            // Cabut webhook lama
            if (StringUtils.hasText(oldToken)) {
                try {
                    telegramApiClient.deleteWebhook(oldToken);
                } catch (Exception e) {
                    log.warn("Failed to delete webhook for old token: {}", e.getMessage());
                }
            }

            // Validasi token baru
            TelegramUserDto telegramUser = fetchTelegramUserFromCloud(newToken);
            bot.setBotToken(newToken);
            bot.setBotUsername(telegramUser.getUsername());

            // Pasang webhook pada token baru
            setupWebhookForBot(bot.getId(), newToken);
        }

        String activeToken = bot.getBotToken();

        // Logika Update Nama Bot & Sinkronisasi setMyName (tanpa perlu konfirmasi ulang saat update)
        if (StringUtils.hasText(requestDto.getBotName()) && !requestDto.getBotName().trim().equals(bot.getBotName())) {
            String newName = requestDto.getBotName().trim();
            log.info("Updating bot name for id={} to '{}'. Synchronizing with Telegram Cloud...", bot.getId(), newName);
            syncNameToTelegramCloud(activeToken, newName);
            bot.setBotName(newName);
        }

        // Update field lainnya jika dikirim
        if (StringUtils.hasText(requestDto.getTenantId())) {
            bot.setTenantId(requestDto.getTenantId());
        }
        if (requestDto.getIsActive() != null) {
            bot.setIsActive(requestDto.getIsActive());
        }
        bot.setUpdatedAt(new Date());

        // Update foto profil bot jika dikirimkan (MinIO & Telegram Cloud)
        handleBotAvatar(bot, activeToken, requestDto.getPhotoBase64());

        TelegramBotEntity updatedBot = telegramBotDataService.save(bot);
        log.info("Telegram bot updated successfully: id={}, username=@{}", updatedBot.getId(), updatedBot.getBotUsername());

        return mapToDto(updatedBot, buildWebhookUrl(updatedBot.getId()));
    }

    @Override
    public List<ResTelegramBotDto> getAllBots() {
        return telegramBotDataService.findAllByIsActiveTrue().stream()
                .map(bot -> mapToDto(bot, buildWebhookUrl(bot.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public ResTelegramBotDto getBotById(String id) {
        TelegramBotEntity bot = telegramBotDataService.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot Telegram dengan ID " + id + " tidak ditemukan!"));
        return mapToDto(bot, buildWebhookUrl(bot.getId()));
    }

    @Override
    public ResTelegramBotDto deleteBot(String id) {
        TelegramBotEntity bot = telegramBotDataService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot Telegram dengan ID " + id + " tidak ditemukan!"));

        // Cabut webhook dari Telegram Cloud
        try {
            telegramApiClient.deleteWebhook(bot.getBotToken());
        } catch (Exception e) {
            log.warn("Failed to delete webhook for bot {}: {}", bot.getId(), e.getMessage());
        }

        // Soft delete di MongoDB
        bot.setIsActive(false);
        bot.setUpdatedAt(new Date());
        TelegramBotEntity deletedBot = telegramBotDataService.save(bot);

        log.info("Telegram bot deactivated: id={}, username=@{}", deletedBot.getId(), deletedBot.getBotUsername());
        return mapToDto(deletedBot, null);
    }

    private TelegramUserDto fetchTelegramUserFromCloud(String token) {
        TelegramApiResponse<TelegramUserDto> getMeResponse;
        try {
            getMeResponse = telegramApiClient.getMe(token);
        } catch (feign.FeignException e) {
            log.error("Telegram API getMe failed with status {}: {}", e.status(), e.contentUTF8());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal validasi Bot Token: Token tidak valid atau tidak terdaftar di Telegram!");
        } catch (Exception e) {
            log.error("Unexpected error calling Telegram getMe: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal menghubungi Telegram API: " + e.getMessage());
        }

        if (getMeResponse == null || !Boolean.TRUE.equals(getMeResponse.getOk()) || getMeResponse.getResult() == null) {
            String desc = (getMeResponse != null && getMeResponse.getDescription() != null)
                    ? getMeResponse.getDescription()
                    : "Token tidak valid atau tidak dapat menghubungi server Telegram";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal validasi Bot Token: " + desc);
        }

        return getMeResponse.getResult();
    }

    private void syncNameToTelegramCloud(String token, String name) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            telegramApiClient.setMyName(token, body);
            log.info("Successfully synced bot name '{}' to Telegram Cloud.", name);
        } catch (Exception e) {
            log.warn("Could not sync bot name '{}' to Telegram Cloud: {}", name, e.getMessage());
        }
    }

    private String setupWebhookForBot(String botId, String token) {
        if (!StringUtils.hasText(webhookBaseUrl)) {
            return null;
        }

        String webhookUrl = buildWebhookUrl(botId);
        try {
            Map<String, Object> webhookBody = new HashMap<>();
            webhookBody.put("url", webhookUrl);
            webhookBody.put("allowed_updates", Collections.singletonList("message"));

            TelegramApiResponse<Boolean> webhookRes = telegramApiClient.setWebhook(token, webhookBody);
            if (webhookRes != null && Boolean.TRUE.equals(webhookRes.getOk())) {
                log.info("Auto-registered webhook for bot {}: {}", botId, webhookUrl);
            } else {
                log.warn("Failed to set webhook for bot {}: {}", botId, webhookRes != null ? webhookRes.getDescription() : "Unknown error");
            }
        } catch (Exception e) {
            log.warn("Error setting webhook for bot {}: {}", botId, e.getMessage());
        }

        return webhookUrl;
    }

    private String buildWebhookUrl(String botId) {
        if (StringUtils.hasText(webhookBaseUrl)) {
            String cleanBase = webhookBaseUrl.endsWith("/")
                    ? webhookBaseUrl.substring(0, webhookBaseUrl.length() - 1)
                    : webhookBaseUrl;
            return cleanBase + PathConstant.ROOT_ENDPOINT_CONTEXT + PathConstant.URI_TELEGRAM_WEBHOOK.replace("{botId}", botId);
        }
        return null;
    }

    private ResTelegramBotDto mapToDto(TelegramBotEntity entity, String webhookUrl) {
        String token = entity.getBotToken();
        String maskedToken = "******";
        if (token != null && token.length() > 10) {
            maskedToken = token.substring(0, 6) + "..." + token.substring(token.length() - 4);
        }

        return ResTelegramBotDto.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .botName(entity.getBotName())
                .botUsername(entity.getBotUsername())
                .botToken(token)
                .maskedToken(maskedToken)
                .isActive(entity.getIsActive())
                .photoUrl(entity.getPhotoUrl())
                .webhookUrl(webhookUrl)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void handleBotAvatar(TelegramBotEntity bot, String rawToken, String photoBase64) {
        if (!StringUtils.hasText(photoBase64)) {
            return;
        }
        try {
            byte[] photoBytes = decodeBase64(photoBase64);
            if (photoBytes == null || photoBytes.length == 0) {
                return;
            }

            if (!StringUtils.hasText(bot.getId())) {
                bot.setId(new org.bson.types.ObjectId().toHexString());
            }

            // 1. Upload foto ke MinIO Storage dengan nama tetap avatar_{botId}.jpg (otomatis me-replace avatar sebelumnya)
            String tenantPrefix = StringUtils.hasText(bot.getTenantId()) ? bot.getTenantId() : "default";
            String objectName = tenantPrefix + "/bots/avatar_" + bot.getId() + ".jpg";

            minioStorageService.uploadFile(objectName, photoBytes, "image/jpeg");
            String streamUrl = minioStorageService.buildStreamUrl(objectName);
            bot.setPhotoUrl(streamUrl);
            log.info("Saved bot profile photo to MinIO (overwriting): {}", streamUrl);

            // 2. Sinkronkan foto profil ke Telegram Cloud via setMyProfilePhoto
            if (StringUtils.hasText(rawToken)) {
                try {
                    telegramMediaService.setBotProfilePhoto(rawToken, photoBytes, "avatar.jpg");
                    log.info("Successfully synced bot profile photo to Telegram Cloud for bot @{}", bot.getBotUsername());
                } catch (Exception e) {
                    log.warn("Failed to sync bot profile photo to Telegram Cloud for bot @{}: {}", bot.getBotUsername(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process bot profile photo: {}", e.getMessage(), e);
        }
    }

    private byte[] decodeBase64(String base64Str) {
        if (!StringUtils.hasText(base64Str)) {
            return null;
        }
        String cleanBase64 = base64Str.trim();
        int commaIndex = cleanBase64.indexOf(",");
        if (commaIndex != -1 && cleanBase64.substring(0, commaIndex).contains("base64")) {
            cleanBase64 = cleanBase64.substring(commaIndex + 1);
        }
        return Base64.getDecoder().decode(cleanBase64);
    }
}
