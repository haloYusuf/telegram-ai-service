package com.techno.berijalan.telegramservice.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqAttachmentDto {

    // "IMAGE", "DOCUMENT", "VIDEO"
    private String mediaType;

    // Untuk file <= 5MB
    private String mediaBase64;

    // Untuk file > 5MB (upload link MinIO atau public URL)
    private String mediaUrl;

    private String fileName;

    private Long fileSize;
}
