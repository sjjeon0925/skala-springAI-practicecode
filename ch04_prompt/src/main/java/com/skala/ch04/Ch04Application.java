package com.skala.ch04;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 5장 — 프롬프트 엔지니어링과 스트리밍.
 *
 * <p>프롬프트 4요소 · Few-shot · CoT · 리소스 파일 템플릿, 그리고 취소·타임아웃을
 * 챙긴 스트리밍까지. 모델을 바꾸기 전에 손볼 곳이 여기다.
 *
 * <p>엔드포인트 — {@code /ch04/classify} · {@code /ch04/reason} · {@code /ch04/stream} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch04Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch04Application.class, args);
    }
}
