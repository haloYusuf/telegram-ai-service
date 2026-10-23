package com.techno.berijalan.telegramservice.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResMediaUploadDto {

    private String mediaUrl;      // Stream URL untuk Frontend / link akses
    private String minioPath;     // Path objek di MinIO
    private String fileName;
    private Long fileSize;
    private String mediaType;     // "IMAGE", "DOCUMENT", "VIDEO"
}
