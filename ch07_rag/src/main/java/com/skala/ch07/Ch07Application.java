package com.skala.ch07;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 8장 — RAG 기본.
 *
 * <p>먼저 {@code POST /ch07/ingest-samples} 로 {@code resources/docs/*.md} 를 적재한 뒤
 * 질문한다. 인제스트를 두 번 해도 재색인 처리 덕에 중복이 쌓이지 않는다.
 *
 * <p>엔드포인트 — {@code /ch07/ingest-samples} · {@code /ch07/ask} · {@code /ch07/retrieve} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch07Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch07Application.class, args);
    }
}
