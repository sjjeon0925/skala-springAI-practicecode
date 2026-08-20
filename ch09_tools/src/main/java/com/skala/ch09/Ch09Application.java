package com.skala.ch09;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 교재 10장 — Tool Calling.
 *
 * <p>모델은 함수를 실행하지 않는다. 어떤 함수를 어떤 인자로 부를지 알려 줄 뿐이고,
 * 실행은 우리 코드가 한다. 그래서 <b>권한 검증도 전적으로 우리 책임</b>이다.
 *
 * <p>엔드포인트 — {@code /ch09/ask} · {@code /ch09/ask-verbose} *
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class Ch09Application {

    public static void main(String[] args) {
        SpringApplication.run(Ch09Application.class, args);
    }
}
