package com.skala.ch10;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 11장 — Tool 안전(승인 게이트 · 감사 로깅).
 *
 * <p>되돌릴 수 없는 일은 도구가 <b>실행하지 않고 접수만</b> 한다.
 * {@code @Tool} 호출은 AOP 로 한곳에서 감사 로그를 남기고 개인정보를 마스킹한다.
 *
 * <p>엔드포인트 — {@code /ch10/ask} · {@code /ch10/approvals} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch10Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch10Application.class, args);
    }
}
