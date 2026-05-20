package com.example.quanlibaixesv.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 401);
        result.put("error", "Unauthorized");
        result.put("message", "Sai mật khẩu");
        return result;
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 400);
        result.put("error", "Bad Request");
        result.put("message", ex.getMessage());
        return result;
    }
}