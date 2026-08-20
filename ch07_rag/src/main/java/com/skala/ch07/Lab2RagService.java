package com.skala.ch07;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Lab2RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public Lab2RagService(ChatClient.Builder builder, VectorStore vectorStore,
            @Value("${lab2.top-k}") int topK,
            @Value("${lab2.similarity-threshold}") double similarityThreshold) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public AnswerDto ask(String question) {
        var docs = retrieve(question, topK);
        if (docs.isEmpty()) {
            return AnswerDto.unknown();      // 근거가 없으면 모델을 부르지 않는다
        }
        return chatClient.prompt()
                .system("""
                        아래 [근거]만 사용해 답한다. 근거에 없으면 "확인되지 않습니다"라고 답한다.
                        추측하지 않는다.
                        sources 필드에는 각 근거 앞의 대괄호 [ ] 안에 적힌 식별자를 그대로 넣는다.
                        문서 제목이나 다른 표현으로 바꾸지 않는다. 실제로 답변에 사용한 근거의 식별자만 넣는다.
                        """)
                .user(u -> u.text("[근거]\n{context}\n\n[질문] {question}")
                        .param("context", format(docs))
                        .param("question", question))
                .call()
                .entity(AnswerDto.class);    // 구조화 출력 — 문자열 파싱 금지(6장)
    }

    private List<Document> retrieve(String question, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
    }

    private String format(List<Document> docs) {
        return docs.stream()
                .map(d -> "[" + d.getMetadata().get("source") + "] " + d.getText())
                .collect(Collectors.joining("\n---\n"));
    }
}
