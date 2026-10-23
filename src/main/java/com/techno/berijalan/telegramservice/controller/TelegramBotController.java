package com.techno.berijalan.telegramservice.controller;

import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqCreateTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqUpdateTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqVerifyTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResVerifyTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.builder.ResponseBerijalan;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramBotBusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Telegram Bot Management", description = "Endpoints untuk registrasi, verifikasi token, update, dan manajemen bot Telegram")
@Validated
@RestController
@RequestMapping(PathConstant.ROOT_ENDPOINT_CONTEXT)
@RequiredArgsConstructor
@Slf4j
public class TelegramBotController {

    private final TelegramBotBusinessService telegramBotBusinessService;

    /**
     * Endpoint untuk memverifikasi token dan mengambil preview info bot dari Telegram sebelum didaftarkan.
     */
    @Operation(summary = "Verifikasi Token Bot Telegram", description = "Mengecek validitas token ke Telegram Cloud dan mengambil preview nama resmi bot sebelum didaftarkan")
    @PostMapping(PathConstant.URI_TELEGRAM_BOT_VERIFY_TOKEN)
    public ResponseEntity<Object> verifyToken(@Valid @RequestBody ReqVerifyTelegramBotDto requestDto) {
        log.info("Received request to verify Telegram Bot token");
        ResVerifyTelegramBotDto response = telegramBotBusinessService.verifyToken(requestDto);
        return ResponseBerijalan.build(response);
    }

    /**
     * Endpoint untuk mendaftarkan bot baru.
     * Otomatis memvalidasi token dan menyelaraskan nama publik bot di Telegram via setMyName jika berbeda.
     */
    @Operation(summary = "Registrasi Bot Baru", description = "Mendaftarkan bot Telegram baru ke sistem dengan auto-sinkronisasi nama via setMyName jika berbeda")
    @PostMapping(PathConstant.URI_TELEGRAM_BOT_CREATE)
    public ResponseEntity<Object> createBot(@Valid @RequestBody ReqCreateTelegramBotDto requestDto) {
        log.info("Received request to register Telegram Bot for tenant: {}", requestDto.getTenantId());
        ResTelegramBotDto response = telegramBotBusinessService.registerBot(requestDto);
        return ResponseBerijalan.buildInsertData(response);
    }

    /**
     * Endpoint untuk memperbarui konfigurasi bot (nama, tenant, token, status aktif).
     * Otomatis menyinkronkan nama ke Telegram Cloud jika nama diubah.
     */
    @Operation(summary = "Update / Edit Bot", description = "Memperbarui nama, status, tenant, atau token bot dengan sinkronisasi ke Telegram Cloud")
    @PutMapping(PathConstant.URI_TELEGRAM_BOT_UPDATE)
    public ResponseEntity<Object> updateBot(@Valid @RequestBody ReqUpdateTelegramBotDto requestDto) {
        log.info("Received request to update Telegram Bot: {}", requestDto.getId());
        ResTelegramBotDto response = telegramBotBusinessService.updateBot(requestDto);
        return ResponseBerijalan.buildUpdateData(response);
    }

    /**
     * Menonaktifkan bot (soft delete) dan mencabut webhook di Telegram Cloud.
     */
    @Operation(summary = "Hapus / Deaktivasi Bot", description = "Melakukan soft-delete bot dan mencabut webhook di Telegram Cloud")
    @DeleteMapping(PathConstant.URI_TELEGRAM_BOT_DELETE)
    public ResponseEntity<Object> deleteBot(@PathVariable String id) {
        log.info("Received request to delete/deactivate Telegram Bot by id: {}", id);
        ResTelegramBotDto response = telegramBotBusinessService.deleteBot(id);
        return ResponseBerijalan.buildDeleteData(response);
    }

    /**
     * Mengambil seluruh daftar bot yang sedang aktif.
     */
    @Operation(summary = "Ambil Semua Bot Aktif", description = "Mengambil daftar seluruh bot Telegram yang aktif di sistem")
    @GetMapping(PathConstant.URI_TELEGRAM_BOT_GET_LIST)
    public ResponseEntity<Object> getAllBots() {
        log.info("Received request to list all active Telegram Bots");
        List<ResTelegramBotDto> response = telegramBotBusinessService.getAllBots();
        return ResponseBerijalan.build(response);
    }

    /**
     * Mengambil detail bot berdasarkan ID.
     */
    @Operation(summary = "Ambil Detail Bot by ID", description = "Mengambil detail informasi bot berdasarkan ID database")
    @GetMapping(PathConstant.URI_TELEGRAM_BOT_GET_DETAIL)
    public ResponseEntity<Object> getBotById(@PathVariable String id) {
        log.info("Received request to get Telegram Bot by id: {}", id);
        ResTelegramBotDto response = telegramBotBusinessService.getBotById(id);
        return ResponseBerijalan.build(response);
    }
}
