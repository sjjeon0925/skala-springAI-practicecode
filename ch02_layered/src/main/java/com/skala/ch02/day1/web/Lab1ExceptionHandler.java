package com.skala.ch02.day1.web;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.skala.ch02.service.OrderNotFoundException;

/**
 * Day1 실습 — 모델 오류·타임아웃 등 예상 밖 실패를 503으로 바꾼다.
 * 상세는 로그에만 남기고, 사용자에게는 추적 ID만 준다.
 *
 * <p>{@code assignableTypes} 로 day1 컨트롤러에만 적용한다. 전역 {@code OrderExceptionHandler}
 * 도 {@code OrderNotFoundException} 을 404로 처리하지만, 어느 advice가 먼저 매칭될지는
 * 보장되지 않는다 — 여기서도 명시적으로 잡아서 {@code Exception.class} 로 새는 걸 막는다.
 */
@RestControllerAdvice(assignableTypes = OrderSummaryController.class)
class Lab1ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(Lab1ExceptionHandler.class);

    /** 없는 주문과 남의 주문이 똑같이 404 다 — 존재 여부를 알리지 않는다. */
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", "주문을 찾을 수 없습니다."));
    }

    /** 요청 자체가 잘못된 경우(파라미터 누락)는 모델 오류가 아니다 — 400으로 그냥 알린다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] 요약 실패", traceId, e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "요약을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.",
                "traceId", traceId));
    }
}
