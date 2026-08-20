package com.skala.ch08;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 9장 — RAG 심화.
 *
 * <p>HyDE · 재순위 · 모듈형 RAG · 하이브리드 검색. 어느 것도 공짜가 아니다 —
 * 질의당 모델 호출이 1~3회 늘어난다. 회수율이 실제로 오르는지 재고 켠다.
 *
 * <p>먼저 {@code POST /ch08/ingest-samples} 로 문서를 넣어야 검색할 것이 생긴다.
 *
 * <p>엔드포인트 — {@code /ch08/ingest-samples} · {@code /ch08/hyde} · {@code /ch08/rerank} · {@code /ch08/modular} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch08Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch08Application.class, args);
    }
}
