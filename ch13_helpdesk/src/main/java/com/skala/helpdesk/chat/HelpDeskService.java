package com.skala.helpdesk.chat;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.skala.helpdesk.advisor.ToolCallTracker;

import reactor.core.publisher.Flux;

/**
 * 13장 Phase 3·5 — 업무 흐름.
 *
 * <p>컨트롤러는 HTTP만 다루고, "질문에 어떻게 답하는가"는 전부 여기 있다.
 * {@code assistantClient}(Advisor 체인 + 도구) 하나만 쓴다 — RAG·메모리·안전·도구
 * 코드는 이 서비스에도 없다. 전부 {@code AiConfig}의 Advisor 체인에 들어 있다.
 */
@Service
public class HelpDeskService {

    private final ChatClient chat;
    private final ChatMemory chatMemory;
    private final ToolCallTracker toolCallTracker;

    public HelpDeskService(@Qualifier("assistantClient") ChatClient chat,
                           ChatMemory chatMemory,
                           ToolCallTracker toolCallTracker) {
        this.chat = chat;
        this.chatMemory = chatMemory;
        this.toolCallTracker = toolCallTracker;
    }

    /** 상담 — 세션 안에서 규정(RAG)·주문 조회(Tool)·환불 접수(승인 게이트)를 모두 다룬다. */
    public AnswerDto ask(String question, String sessionId, String userId) {
        toolCallTracker.reset();      // 이번 요청에서 도구가 불렸는지 새로 센다

        ChatClientResponse response = chat.prompt()
                .user(question)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .chatClientResponse();

        String answer = response.chatResponse().getResult().getOutput().getText();
        boolean toolUsed = toolCallTracker.wasCalled();
        toolCallTracker.reset();

        return new AnswerDto(answer, extractSources(response), toolUsed);
    }

    /**
     * 상담 — 스트리밍(SSE)판. 긴 답변에서 첫 글자를 빨리 보여준다.
     *
     * <p>토큰이 조각조각 오는 동안은 {@code toolCallTracker}로 도구 사용 여부를
     * 판단할 수 없다(끝나기 전이라서) — 그래서 스트리밍 응답에는 {@code toolUsed}·
     * {@code sources}를 함께 실어 보내지 않는다. 필요하면 동기 API(/lab3/chat)를 쓴다.
     */
    public Flux<String> stream(String question, String sessionId, String userId) {
        return chat.prompt()
                .user(question)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }

    /** 대화 이력 확인 — Advisor 순서 실험(차단 문구가 이력에 남는지)에 쓴다. */
    public List<String> history(String sessionId) {
        return chatMemory.get(sessionId).stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .toList();
    }

    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }

    /** RAG가 실제로 근거로 쓴 문서명을 꺼낸다 — 없으면 빈 목록(도구/일반 답변인 경우). */
    @SuppressWarnings("unchecked")
    private List<String> extractSources(ChatClientResponse response) {
        Object raw = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Document>) list).stream()
                .map(d -> String.valueOf(d.getMetadata().get("source")))
                .distinct()
                .toList();
    }
}
