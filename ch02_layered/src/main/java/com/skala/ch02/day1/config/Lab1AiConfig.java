package com.skala.ch02.day1.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Day1 실습 — 용도별 ChatClient 빈.
 *
 * <p>온도·최대 토큰을 빈에서 못 박아 둔다. 호출부가 정하게 두면
 * 누군가는 기본값(0.7)으로 부르고, 그날부터 요약이 매번 달라진다.
 */
@Configuration
class Lab1AiConfig {

    @Bean
    ChatClient summaryChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        너는 이커머스 주문 상태 도우미다.
                        주어진 주문 정보만 사용해 한국어 한 문장으로 요약한다.
                        추측하지 않는다. 정보가 부족하면 "정보가 부족합니다"라고 답한다.
                        """)
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.0)
                        .maxTokens(120)
                        .build())
                .build();
    }
}
