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
public class ReqSendTelegramMessageDto {

    @NotBlank(message = "Bucket ID wajib diisi!")
    private String bucketId;

    // Optional jika mengirim media tanpa teks/caption
    private String messageText;

    private String agentId;

    private String agentName;

    // "IMAGE", "DOCUMENT", "VIDEO"
    private String mediaType;

    // Untuk file <= 5MB
    private String mediaBase64;

    // Untuk file > 5MB (upload link MinIO atau public URL)
    private String mediaUrl;

    private String fileName;

    private Long fileSize;

    // Relasi Reply / Quote ke pesan sebelumnya
    private String replyToMessageId;

    // Multi-Attachment (untuk pengiriman > 1 file dalam 1 bubble)
    private java.util.List<ReqAttachmentDto> attachments;
}
