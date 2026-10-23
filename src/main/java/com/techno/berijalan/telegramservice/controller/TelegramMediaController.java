package com.techno.berijalan.telegramservice.controller;

import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import com.techno.berijalan.telegramservice.domain.dto.response.ResMediaUploadDto;
import com.techno.berijalan.telegramservice.domain.dto.response.builder.ResponseBerijalan;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping(PathConstant.ROOT_ENDPOINT_CONTEXT)
@RequiredArgsConstructor
@Tag(name = "Telegram Media Storage", description = "Endpoint untuk upload, streaming, dan manajemen file media Telegram (MinIO)")
public class TelegramMediaController {

    private final TelegramMediaService telegramMediaService;

    @PostMapping(value = PathConstant.URI_TELEGRAM_MEDIA_UPLOAD, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload media file ke MinIO (>5MB sampai 10MB)")
    public ResponseEntity<Object> uploadMedia(@RequestParam("file") MultipartFile file) {
        log.info("[TelegramMediaController] Received request to upload media file: {}", file != null ? file.getOriginalFilename() : "null");
        ResMediaUploadDto resDto = telegramMediaService.uploadMedia(file);
        return ResponseBerijalan.build(resDto);
    }

    @GetMapping(PathConstant.URI_TELEGRAM_MEDIA_STREAM)
    @Operation(summary = "Stream file media dari MinIO")
    public ResponseEntity<byte[]> streamMedia(
            @RequestParam("path") String path,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download
    ) {
        log.info("[TelegramMediaController] Received request to stream media from path: {}, download={}", path, download);
        return telegramMediaService.streamMedia(path, download);
    }
}
