package com.skala.ch12;

import org.springframework.retry.annotation.EnableRetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 13장 — 운영(재시도 · 폴백 · 시맨틱 캐시).
 *
 * <p>{@code @EnableRetry} 가 있어야 {@code @Retryable} · {@code @Recover} 가 동작한다.
 * 재시도 대상은 일시적 오류뿐이다 — 4xx 를 재시도하면 같은 실패를 돈 주고 반복할 뿐이다.
 *
 * <p>엔드포인트 — {@code /ch12/ask} · {@code /ch12/semantic} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@EnableRetry
@SpringBootApplication
public class Ch12Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch12Application.class, args);
    }
}
