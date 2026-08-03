package com.example.minecraftserver.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyResponse<T> {
    private boolean success;
    private Integer code;
    private String message;
    private T data;
    private int status;

    public static <T> ResponseEntity<MyResponse<T>> success() {
        return ResponseEntity.ok(new MyResponse<>(true, null, null, null, 200));
    }

    public static <T> ResponseEntity<MyResponse<T>> success(T data) {
        return ResponseEntity.ok(new MyResponse<>(true, null, null, data, 200));
    }

    public static <T> ResponseEntity<MyResponse<T>> error(MyException exception) {
        return error(exception.getError(), ErrorCode.resolveStatus(exception.getError()), exception.getMessage());
    }

    public static <T> ResponseEntity<MyResponse<T>> error(ErrorCode error, HttpStatus status) {
        return error(error, status, error.getError());
    }

    public static <T> ResponseEntity<MyResponse<T>> error(ErrorCode error, HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new MyResponse<>(false, error.getCode(), message, null, status.value()));
    }
}