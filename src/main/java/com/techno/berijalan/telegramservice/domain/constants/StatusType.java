package com.techno.berijalan.telegramservice.domain.constants;

import lombok.Getter;

@Getter
public enum StatusType {
    RESULT_SUCCESS("RESULT_SUCCESS", "Success"),
    RESULT_FAILED("RESULT_FAILED", "Failed"),
    RESULT_SUCCESS_INSERT_DATA("RESULT_SUCCESS_INSERT_DATA", "Success Insert Data"),
    RESULT_SUCCESS_UPDATE_DATA("RESULT_SUCCESS_UPDATE_DATA", "Success Update Data"),
    RESULT_SUCCESS_DELETE_DATA("RESULT_SUCCESS_DELETE_DATA", "Success Delete Data");

    private final String code;
    private final String message;

    StatusType(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
