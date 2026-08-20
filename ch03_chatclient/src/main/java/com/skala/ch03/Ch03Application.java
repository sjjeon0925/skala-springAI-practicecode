package com.skala.ch03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 4장 — ChatClient 기본기와 용도별 빈 구성.
 *
 * <p>추출용(temperature 0)과 상담용(0.7)을 빈으로 나눠 두면 호출부가 매번 옵션을
 * 덮어쓰지 않아도 된다. 이 프로젝트의 {@code ChatClientConfig} 가 그 본보기다.
 *
 * <p>엔드포인트 — {@code /ch03/ask} · {@code /ch03/ask-as} · {@code /ch03/translate} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch03Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch03Application.class, args);
    }
}
