package com.skala.ch03;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 2장 — ChatClient 빈을 용도별로 나눠 만든다.
 *
 * <p>하나의 ChatClient 로 모든 일을 시키면 기본값이 서로 충돌한다.
 * 용도별 빈으로 나누면 호출부가 옵션을 매번 덮어쓸 필요가 없다.
 */
@Configuration
public class ChatClientConfig {

    /** 요약 */
    @Bean
    public ChatClient summaryChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        너는 텍스트 내용 요약 에이전트다.
                        - 주어진 텍스트에 없는 내용은 절대 만들어 내지 않는다.
                        - 기존 원문과 비슷한 수준의 용어를 사용한다.""")
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.0)
                        .maxTokens(1024)
                        .build())
                .build();
    }

    /** 아이디어 생성 */
    @Bean
    public ChatClient ideaChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        너는 친절하고 창의적인 아이디어 생성 도우미이다.
                        - 존댓말을 쓰고 3문장 이내로 답한다.
                        - 줄글이 아닌 개조형 형식으로 답변한다.""")
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.9)
                        .build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
