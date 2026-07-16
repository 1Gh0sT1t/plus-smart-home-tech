package ru.yandex.practicum.telemetry.collector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationError(MethodArgumentNotValidException e) {
        log.warn("Ошибка валидации запроса: {}", e.getMessage());
        return Map.of("error", "Некорректные данные запроса: " + e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadableMessage(HttpMessageNotReadableException e) {
        log.warn("Не удалось разобрать тело запроса: {}", e.getMessage());
        return Map.of("error", "Не удалось разобрать тело запроса: " + e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NoResourceFoundException e) {
        log.warn("Запрос к несуществующему пути: {}", e.getResourcePath());
        return Map.of("error", "Ресурс не найден: " + e.getResourcePath());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleInternalError(Exception e) {
        log.error("Внутренняя ошибка сервиса", e);
        return Map.of("error", "Внутренняя ошибка сервиса: " + e.getMessage());
    }
}
