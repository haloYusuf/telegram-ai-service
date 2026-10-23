package com.techno.berijalan.telegramservice.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramApiResponse<T> {

    private Boolean ok;

    private T result;

    @JsonProperty("error_code")
    private Integer errorCode;

    private String description;
}
