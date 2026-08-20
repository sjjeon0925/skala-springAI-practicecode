package com.skala.ch07;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 6장 — RAG 질의(Retrieval + Generation).
 *
 * <p>{@link QuestionAnswerAdvisor} 가 "검색 → 근거 주입" 을 대신 처리한다.
 * 다만 <b>출처 표기는 우리가 꺼내 붙여야</b> 한다 — 검색된 문서는 응답 컨텍스트에 담겨 온다.
 */
@Service
public class RagService {

    private final ChatClient chat;
    private final VectorStore vectorStore;

    public RagService(@Qualifier("supportClient") ChatClient chat, VectorStore vectorStore) {
        this.chat = chat;
        this.vectorStore = vectorStore;
    }

    public record Source(String document, String version) {}

    public record Answer(String text, List<Source> sources) {}

    /** 기본 RAG — Advisor 한 줄이면 평범한 질문이 근거 있는 질문이 된다. */
    public Answer ask(String question) {
        return ask(question, null, null);
    }

    /**
     * 부서 필터 + 대화 메모리까지 함께 쓰는 형태.
     *
     * @param dept           null 이 아니면 해당 부서 문서로만 검색 범위를 좁힌다(보안 경계이기도 하다)
     * @param conversationId null 이 아니면 이 대화의 이력을 이어 간다
     */
    public Answer ask(String question, String dept, String conversationId) {
        SearchRequest.Builder search = SearchRequest.builder()
                .topK(5)
                .similarityThreshold(0.62);
        if (dept != null) {
            search.filterExpression("dept == '" + dept + "'");
        }

        var qa = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(search.build())
                .build();

        var spec = chat.prompt()
                .system("""
                        너는 사내 규정 안내 도우미다.
                        - 반드시 주어진 근거 문서 안의 내용만으로 답한다.
                        - 근거에서 답을 찾을 수 없으면 "제공된 문서에서 확인되지 않습니다"라고 답한다.
                        - 추측하거나 일반 상식으로 채우지 않는다.""")
                .user(question)
                .advisors(qa);

        if (conversationId != null) {
            spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }

        ChatClientResponse response = spec.call().chatClientResponse();
        return new Answer(response.chatResponse().getResult().getOutput().getText(),
                extractSources(response));
    }

    /** 어떤 근거가 실제로 쓰였는지 꺼낸다. 출처 없는 답은 검증할 수 없는 답이다. */
    @SuppressWarnings("unchecked")
    private List<Source> extractSources(ChatClientResponse response) {
        Object raw = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Document>) list).stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("version"))))
                .distinct()
                .toList();
    }

    /** Advisor 없이 직접 검색해 보고 싶을 때 — 무엇이 회수됐는지 눈으로 확인하는 용도. */
    public List<String> retrieveOnly(String question, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());
        return hits == null ? List.of() : hits.stream().map(Document::getText).toList();
    }
}
