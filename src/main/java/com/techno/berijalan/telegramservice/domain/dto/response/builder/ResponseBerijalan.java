package com.techno.berijalan.telegramservice.domain.dto.response.builder;

import com.techno.berijalan.telegramservice.domain.constants.StatusType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Setter
@Getter
public class ResponseBerijalan<T> {

    private String resultCode;
    private Integer httpStatus;
    private String message;
    private T data;

    public static <T> ResponseEntity<Object> build(T resultData) {
        ResponseAccNoPage<T> response = new ResponseAccNoPage<>();
        response.setData(resultData);
        response.setMessage(StatusType.RESULT_SUCCESS.getMessage());
        response.setStatus(HttpStatus.OK.value());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public static <T> ResponseEntity<Object> buildInsertData(T resultData) {
        ResponseAccNoPage<T> response = new ResponseAccNoPage<>();
        response.setData(resultData);
        response.setMessage(StatusType.RESULT_SUCCESS_INSERT_DATA.getMessage());
        response.setStatus(HttpStatus.CREATED.value());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    public static <T> ResponseEntity<Object> buildUpdateData(T resultData) {
        ResponseAccNoPage<T> response = new ResponseAccNoPage<>();
        response.setData(resultData);
        response.setMessage(StatusType.RESULT_SUCCESS_UPDATE_DATA.getMessage());
        response.setStatus(HttpStatus.OK.value());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public static <T> ResponseEntity<Object> buildDeleteData(T resultData) {
        ResponseAccNoPage<T> response = new ResponseAccNoPage<>();
        response.setData(resultData);
        response.setMessage(StatusType.RESULT_SUCCESS_DELETE_DATA.getMessage());
        response.setStatus(HttpStatus.OK.value());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public static <T> ResponseEntity<Object> buildError(String errorMessage, HttpStatus httpStatus) {
        ResponseAccNoPage<T> response = new ResponseAccNoPage<>();
        response.setMessage(errorMessage);
        response.setStatus(httpStatus.value());
        return new ResponseEntity<>(response, httpStatus);
    }

    @Setter
    @Getter
    public static class ResponseAccNoPage<T> {
        private Integer status;
        private String message;
        private T data;
    }
}
