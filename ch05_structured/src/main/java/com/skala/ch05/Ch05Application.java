package com.skala.ch05;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 6장 — 구조화 출력과 멀티모달.
 *
 * <p>문자열을 파싱하지 말고 객체로 받는다. 목록·중첩·실패 복구까지 한 서비스에 모았고,
 * 이미지 입력({@code /ch05/receipt})은 멀티모달과 구조화 출력의 결합 예다.
 *
 * <p>엔드포인트 — {@code /ch05/classify} · {@code /ch05/keywords} · {@code /ch05/company} · {@code /ch05/receipt} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch05Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch05Application.class, args);
    }
}
