package com.example.minecraftserver.exception;

import lombok.Getter;

@Getter
public class MyException extends RuntimeException {

    private final ErrorCode error;

    public MyException(ErrorCode error) {
        super(error.getError());
        this.error = error;
    }

    public static void throwIf(boolean condition, ErrorCode error) {
        if (condition) {
            throw new MyException(error);
        }
    }
}
