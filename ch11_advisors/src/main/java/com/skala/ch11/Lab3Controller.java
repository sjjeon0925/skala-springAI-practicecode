package com.skala.ch11;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Day 3 실습 — 상담 에이전트.
 *
 * <p>{@code assistantClient}(Advisor 체인 + 도구)만 쓴다. 이 컨트롤러엔
 * RAG·메모리·안전·도구 코드가 한 줄도 없다 — 전부 {@code MemoryChatConfig}에 있다.
 */
@RestController
public class Lab3Controller {

    private final ChatClient chat;
    private final IngestService ingest;
    private final ApprovalTools approvalTools;
    private final ChatMemory chatMemory;

    public Lab3Controller(@Qualifier("assistantClient") ChatClient chat,
                          IngestService ingest,
                          ApprovalTools approvalTools,
                          ChatMemory chatMemory) {
        this.chat = chat;
        this.ingest = ingest;
        this.approvalTools = approvalTools;
        this.chatMemory = chatMemory;
    }

    /** 규정 문서를 먼저 넣는다 — 안 하면 RAG 답변이 나오지 않는다. */
    @PostMapping("/lab3/ingest-samples")
    public List<IngestService.IngestResult> ingestSamples() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/docs/*.md");
        return java.util.Arrays.stream(docs)
                .map(doc -> ingest.ingest(doc, "handbook", "CS"))
                .toList();
    }

    /**
     * 상담 API — 세션 안에서 규정(RAG)·주문 조회(Tool)·환불 접수(승인 게이트)를 모두 다룬다.
     * curl 'localhost:8080/lab3/chat?q=단순 변심 반품은 며칠 이내인가요&sessionId=s1&userId=user-1'
     */
    @GetMapping("/lab3/chat")
    public Map<String, Object> chat(@RequestParam String q,
                                    @RequestParam(defaultValue = "demo") String sessionId,
                                    @RequestParam(defaultValue = "user-1") String userId) {
        ChatClientResponse response = chat.prompt()
                .user(q)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .chatClientResponse();

        String answer = response.chatResponse().getResult().getOutput().getText();
        return Map.of("answer", answer, "sessionId", sessionId, "sources", extractSources(response));
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

    /** 대화 이력 확인 — Advisor 순서 실험(차단 문구가 이력에 남는지)에 쓴다. */
    @GetMapping("/lab3/chat/history")
    public List<String> history(@RequestParam(defaultValue = "demo") String sessionId) {
        return chatMemory.get(sessionId).stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .toList();
    }

    @DeleteMapping("/lab3/chat/history")
    public void clearHistory(@RequestParam(defaultValue = "demo") String sessionId) {
        chatMemory.clear(sessionId);
    }

    /** 담당자 화면 — 대기 중인 환불 요청 목록. */
    @GetMapping("/lab3/admin/tickets/pending")
    public List<ApprovalTools.Approval> pending() {
        return approvalTools.pending();
    }

    @PostMapping("/lab3/admin/tickets/approve")
    public ApprovalTools.Approval approve(@RequestParam String id) {
        return approvalTools.approve(id);
    }
}
