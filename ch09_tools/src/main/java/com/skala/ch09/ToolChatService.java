package com.skala.ch09;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 8장 — 도구를 붙여 대화한다.
 *
 * <p>모델은 "함수를 실행"하지 않는다. <b>어떤 함수를 어떤 인자로 부를지 알려 줄 뿐</b>이고,
 * 실제 실행은 Spring AI 가 우리 코드로 한다. 그래서 권한·검증은 전적으로 우리 책임이다.
 */
@Service
public class ToolChatService {

    private final ChatClient chat;
    private final WeatherTools weatherTools;
    private final OrderTools orderTools;

    public ToolChatService(@Qualifier("supportClient") ChatClient chat,
                           WeatherTools weatherTools,
                           OrderTools orderTools) {
        this.chat = chat;
        this.weatherTools = weatherTools;
        this.orderTools = orderTools;
    }

    /** 도구가 필요 없는 질문에는 모델이 그냥 답한다. */
    public String ask(String question, String userId) {
        return chat.prompt()
                .user(question)
                .tools(weatherTools, orderTools)
                // 사용자 ID 는 프롬프트가 아니라 이 통로로 — 모델이 바꿔 부를 수 없다
                .toolContext(Map.of("userId", userId))
                .call()
                .content();
    }

    /** 도구가 실제로 불렸는지 확인하고 싶을 때 — 응답 메타데이터를 함께 본다. */
    public Map<String, Object> askVerbose(String question, String userId) {
        ChatResponse response = chat.prompt()
                .user(question)
                .tools(weatherTools, orderTools)
                .toolContext(Map.of("userId", userId))
                .call()
                .chatResponse();

        return Map.of(
                "answer", response.getResult().getOutput().getText(),
                "finishReason", String.valueOf(response.getResult().getMetadata().getFinishReason()),
                "promptTokens", response.getMetadata().getUsage().getPromptTokens(),
                "completionTokens", response.getMetadata().getUsage().getCompletionTokens());
    }
}
