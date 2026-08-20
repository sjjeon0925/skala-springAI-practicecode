package com.skala.ch11;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 12장 — Advisor 순서 · 대화 메모리 · 토큰 계측.
 *
 * <p>Advisor 는 양파 껍질처럼 요청을 감싼다. order 가 낮은 것이 바깥이다.
 * <b>차단(SafeGuard)은 언제나 저장(Memory)보다 앞</b>이라는 것이 이 장의 핵심이다.
 *
 * <p>엔드포인트 — {@code /ch11/ingest-samples} · {@code /ch11/chat} · {@code /ch11/metrics} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch11Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch11Application.class, args);
    }
}
