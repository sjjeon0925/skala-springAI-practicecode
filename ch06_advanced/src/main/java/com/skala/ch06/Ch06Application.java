package com.skala.ch06;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 7장 — LLM 워크플로 패턴.
 *
 * <p>라우팅 · 병렬 · 평가-교정 · 오케스트레이션, 그리고 "호출하지 않는" 캐시까지.
 * 병렬 호출은 전용 스레드 풀({@code AiExecutorConfig})로 동시 호출 상한을 묶는다.
 *
 * <p>엔드포인트 — {@code /ch06/route} · {@code /ch06/write} · {@code /ch06/orchestrate} · {@code /ch06/cached} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch06Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch06Application.class, args);
    }
}
