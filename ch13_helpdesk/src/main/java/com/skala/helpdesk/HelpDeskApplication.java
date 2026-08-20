package com.skala.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 13장 — 종합실습: HelpDesk AI.
 *
 * <p>ch11_advisors(Advisor 체인·메모리·RAG)와 ch09/ch10(주문 도구·승인 게이트)을
 * 한 서비스로 합친 캡스톤이다. Advisor는 양파 껍질처럼 요청을 감싼다 —
 * <b>차단(SafeGuard)은 언제나 저장(Memory)보다 앞</b>이라는 원칙은 그대로 이어진다.
 *
 * <p>엔드포인트 — {@code /lab3/ingest-samples} · {@code /lab3/chat} · {@code /lab3/admin/tickets/*}
 * <pre>
 *   ./gradlew bootRun          # http://localhost:8080
 *   http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication
public class HelpDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }
}
