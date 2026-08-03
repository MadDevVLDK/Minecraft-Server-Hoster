package com.example.minecraftserver.config;

import java.nio.file.AccessDeniedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MyException.class)
    public ResponseEntity<MyResponse<Void>> handleMyException(MyException ex) {
        return MyResponse.error(ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MyResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return MyResponse.error(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_ERROR.getError() + " --> " + errors
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MyResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return MyResponse.error(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MyResponse<Void>> handleGeneric(Exception ex) {
        if (ex instanceof NoHandlerFoundException) {
            return MyResponse.error(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        log.error("Error", ex);
        return MyResponse.error(
            ErrorCode.INTERNAL_SERVER_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_SERVER_ERROR.getError() + " --> " + ex.getMessage()
        );
    }
}