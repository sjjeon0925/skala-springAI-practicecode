package com.example.step06;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * STEP 06 - RAG 질의 컨트롤러
 *
 * <p>컨트롤러 코드에는 '검색'이라는 단어조차 없다.
 * 문서 검색과 Context 주입을 QuestionAnswerAdvisor가 전부 처리하기 때문이다.
 * 이것이 Advisor 조합 방식의 장점이다.
 *
 * <p><b>확인 방법</b>
 * <pre>
 *   # 적재된 문서에 있는 내용 → 문서 기반으로 답변
 *   GET /api/rag?question=Advisor는 무엇인가요?
 *
 *   # 문서에 없는 내용 → "모른다"고 답해야 정상 (환각 방지 확인)
 *   GET /api/rag?question=오늘 서울 날씨 알려줘
 * </pre>
 */
@RestController
public class RagController {

    private final ChatClient chatClient;

    /**
     * {@link RagConfig#ragChatClient} Bean이 주입된다.
     * (컨텍스트에 ChatClient 타입 Bean이 이것 하나뿐이라 이름 없이도 주입된다)
     */
    public RagController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/api/rag")
    public String rag(@RequestParam String question) {
        return chatClient
                .prompt()
                // 이 질문 문자열이 그대로 임베딩되어 유사 문서 검색에 사용된다.
                .user(question)
                .call()
                .content();
    }
}
