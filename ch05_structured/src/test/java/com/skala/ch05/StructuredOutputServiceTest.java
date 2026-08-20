package com.skala.ch05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.skala.ch05.StructuredOutputService;

/**
 * AI 코드도 테스트한다 — 다만 <b>모델의 답 내용</b>이 아니라
 * <b>우리 코드가 응답을 어떻게 다루는지</b>를 검증한다.
 *
 * <p>실제 모델을 부르면 느리고, 비싸고, 결과가 매번 달라 테스트가 흔들린다.
 * ChatModel 을 모킹하면 전부 해결된다.
 */
class StructuredOutputServiceTest {

    /** 고정된 응답을 돌려주는 모의 ChatModel 을 만든다. */
    private ChatModel mockModel(String responseText) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(responseText))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }

    @Test
    @DisplayName("정상 JSON 응답이면 record 로 변환된다")
    void 정상_응답은_객체로_변환된다() {
        var model = mockModel("""
                {"category":"BILLING","priority":"HIGH","summary":"중복 결제",
                 "tags":["결제","중복"]}""");

        var chatClient = org.springframework.ai.chat.client.ChatClient.builder(model).build();
        var service = new StructuredOutputService(chatClient);

        StructuredOutputService.Ticket ticket = service.classify("카드가 두 번 결제됐어요");

        assertThat(ticket.category()).isEqualTo("BILLING");
        assertThat(ticket.priority()).isEqualTo("HIGH");
        assertThat(ticket.tags()).contains("결제");
    }

    @Test
    @DisplayName("형식이 깨진 응답이어도 서비스가 죽지 않고 기본값을 돌려준다")
    void 형식_위반시_안전한_기본값을_돌려준다() {
        // 모델이 설명을 덧붙여 JSON 파싱이 깨지는 흔한 상황
        var model = mockModel("죄송합니다, 분류하기 어려운 문의입니다.");

        var chatClient = org.springframework.ai.chat.client.ChatClient.builder(model).build();
        var service = new StructuredOutputService(chatClient);

        StructuredOutputService.Ticket ticket = service.classifySafely("무슨 말인지 모르겠는 문의");

        assertThat(ticket).isNotNull();
        assertThat(ticket.category()).isEqualTo("ETC");   // 예외가 아니라 기본값
    }
}
