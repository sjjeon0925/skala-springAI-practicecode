package com.skala.ch05;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * 4장 — 구조화 출력.
 *
 * <p>문자열을 파싱하지 말고 객체로 받는다. 그래야 컴파일러가 지켜 준다.
 */
@Service
public class StructuredOutputService {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputService.class);

    private final ChatClient chat;

    public StructuredOutputService(@Qualifier("extractClient") ChatClient chat) {
        this.chat = chat;
    }

    // ── 도메인 타입 ────────────────────────────────────────────
    public record Ticket(String category, String priority, String summary, List<String> tags) {}

    public record Keyword(String term, double score) {}

    public record Contact(String name, String email, String phone) {}

    public record Company(String name, String industry, List<Contact> contacts) {}

    /** ① 한 줄로 객체 받기 — 가장 흔하게 쓰는 형태. */
    public Ticket classify(String inquiry) {
        return chat.prompt()
                .user("다음 고객 문의를 분류하라. category 는 BILLING·DELIVERY·REFUND·ETC, "
                        + "priority 는 HIGH·NORMAL·LOW 중 하나다.\n---\n" + inquiry)
                .call()
                .entity(Ticket.class);
    }

    /** ② 목록으로 받기 — 제네릭은 ParameterizedTypeReference 로 넘긴다. */
    public List<Keyword> keywords(String text) {
        return chat.prompt()
                .user("다음 글의 핵심 키워드 5개를 중요도 점수(0~1)와 함께 뽑아라.\n---\n" + text)
                .call()
                .entity(new ParameterizedTypeReference<List<Keyword>>() {});
    }

    /** ③ 중첩 구조 — record 안에 record 를 넣어도 그대로 동작한다. */
    public Company extractCompany(String document) {
        return chat.prompt()
                .user("다음 문서에서 회사 정보와 담당자 목록을 추출하라. 없는 값은 null 로 둔다.\n---\n" + document)
                .call()
                .entity(Company.class);
    }

    /**
     * ④ converter 를 직접 써서 형식 지시문의 위치를 내가 정한다.
     * {@code entity()} 는 이 과정을 한 줄로 줄인 것이다.
     */
    public Ticket classifyWithExplicitFormat(String inquiry) {
        var converter = new BeanOutputConverter<>(Ticket.class);

        String answer = chat.prompt()
                .user(u -> u.text("""
                        다음 고객 문의를 분류하라.
                        {format}
                        ---
                        문의: {inquiry}""")
                        .param("format", converter.getFormat())
                        .param("inquiry", inquiry))
                .call()
                .content();

        return converter.convert(answer);
    }

    /**
     * ⑤ 실패 복구 — 온도 0, 형식만 재요청, 마지막엔 안전한 기본값.
     * 구조화 출력은 "거의" 성공한다. 그 '거의'가 운영에서는 장애 알람이다.
     */
    public Ticket classifySafely(String inquiry) {
        try {
            return classify(inquiry);

        } catch (Exception first) {
            log.warn("구조화 출력 실패 — 형식만 다시 요청한다", first);
            try {
                return chat.prompt()
                        .system("반드시 JSON 객체 하나만 출력한다. 설명·코드펜스·주석 금지.")
                        .user("아래 문의를 스키마에 맞게 다시 분류하라.\n---\n" + inquiry)
                        .options(ChatOptions.builder().temperature(0.0).build())
                        .call()
                        .entity(Ticket.class);

            } catch (Exception second) {
                log.error("재요청도 실패 — 기본값을 반환한다", second);
                return new Ticket("ETC", "NORMAL", "자동 분류 실패", List.of());
            }
        }
    }
}
