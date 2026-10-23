package com.techno.berijalan.telegramservice.controller;

import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqSendTelegramMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResSendTelegramMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramBucketDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.builder.ResponseBerijalan;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramChatBusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.techno.berijalan.telegramservice.service.businessservice.TelegramSseEmitterService;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Telegram Outbound Chat & History", description = "Endpoints untuk pengiriman pesan balasan CS ke nasabah dan pengambilan riwayat percakapan")
@Validated
@RestController
@RequestMapping(PathConstant.ROOT_ENDPOINT_CONTEXT)
@RequiredArgsConstructor
@Slf4j
public class TelegramChatController {

    private final TelegramChatBusinessService telegramChatBusinessService;
    private final TelegramSseEmitterService telegramSseEmitterService;

    @Operation(summary = "Real-Time Chat Stream (SSE)", description = "Membuka koneksi HTTP streaming (Server-Sent Events) untuk menerima event pesan masuk secara real-time per bot")
    @GetMapping(value = PathConstant.URI_TELEGRAM_CHAT_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatEvents(@RequestParam String botId) {
        log.info("Received request to open SSE chat stream for botId={}", botId);
        return telegramSseEmitterService.subscribe(botId);
    }

    @Operation(summary = "Kirim Balasan Pesan CS", description = "Mengirimkan pesan balasan dari agen CS di web dashboard ke nasabah Telegram")
    @PostMapping(PathConstant.URI_TELEGRAM_CHAT_SEND)
    public ResponseEntity<Object> sendMessage(@Valid @RequestBody ReqSendTelegramMessageDto requestDto) {
        log.info("Received request to send CS message to bucketId={}", requestDto.getBucketId());
        ResSendTelegramMessageDto response = telegramChatBusinessService.sendMessage(requestDto);
        return ResponseBerijalan.build(response);
    }

    @Operation(summary = "Ambil Riwayat Obrolan Room", description = "Mengambil seluruh riwayat percakapan pada tiket/room percakapan secara kronologis")
    @GetMapping(PathConstant.URI_TELEGRAM_CHAT_HISTORY)
    public ResponseEntity<Object> getChatHistory(@PathVariable String bucketId) {
        log.info("Received request to get chat history for bucketId={}", bucketId);
        List<ResTelegramChatMessageDto> response = telegramChatBusinessService.getChatHistory(bucketId);
        return ResponseBerijalan.build(response);
    }

    @Operation(summary = "Ambil Daftar Antrean Percakapan (CS Inbox)", description = "Mengambil daftar room percakapan/tiket nasabah untuk dashboard agen, dengan cuplikan pesan terakhir")
    @GetMapping(PathConstant.URI_TELEGRAM_CHAT_BUCKETS)
    public ResponseEntity<Object> getBucketList(
            @RequestParam(required = false) String botId,
            @RequestParam(required = false) String status) {
        log.info("Received request to get bucket list: botId={}, status={}", botId, status);
        List<ResTelegramBucketDto> response = telegramChatBusinessService.getBucketList(botId, status);
        return ResponseBerijalan.build(response);
    }

    @Operation(summary = "Toggle Status Sesi Percakapan (Compact 1 Endpoint)", description = "Mengubah status tiket secara dinamis (RESOLVED <-> ON_GOING) dalam 1 endpoint")
    @PutMapping(PathConstant.URI_TELEGRAM_CHAT_TOGGLE_STATUS)
    public ResponseEntity<Object> toggleBucketStatus(@PathVariable String bucketId) {
        log.info("Received request to toggle bucket status for bucketId={}", bucketId);
        ResTelegramBucketDto response = telegramChatBusinessService.toggleBucketStatus(bucketId);
        return ResponseBerijalan.build(response);
    }
}


