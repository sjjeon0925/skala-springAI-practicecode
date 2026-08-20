package com.example.step06;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STEP 06 - RAG(Retrieval Augmented Generation) 설정
 *
 * <p><b>RAG란?</b><br>
 * LLM은 학습 시점 이후의 지식이나 사내 문서를 알지 못하고, 모르면 그럴듯하게
 * 지어내는(환각, hallucination) 특성이 있다. RAG는 질문과 관련된 문서를
 * <b>먼저 검색해서 프롬프트에 근거로 넣어 준 뒤</b> 답하게 하는 패턴이다.
 *
 * <pre>
 *   질문 → [임베딩] → 벡터 유사도 검색 → 관련 문서 K개
 *        → 질문 + 문서를 함께 프롬프트로 구성 → LLM → 근거 있는 답변
 * </pre>
 *
 * <p><b>임베딩(Embedding)</b> : 텍스트를 의미가 가까울수록 거리도 가까운
 * 숫자 벡터로 바꾼 것. 키워드가 정확히 일치하지 않아도
 * "의미가 비슷한" 문서를 찾을 수 있는 이유다.
 * 이 프로젝트는 application.yml에서 {@code text-embedding-3-small}을 사용한다.
 *
 * <p><b>VectorStore</b> : 그 벡터를 저장하고 유사도 검색을 수행하는 저장소.
 * 여기서는 PostgreSQL 확장인 <b>pgvector</b>를 쓴다.
 * {@code spring-ai-starter-vector-store-pgvector} 의존성 덕분에
 * {@code VectorStore} Bean과 테이블(vector_store)이 자동 구성된다.
 */
@Configuration
public class RagConfig {

    /**
     * RAG용 ChatClient를 조립한다.
     *
     * @param vectorStore 자동 구성된 pgvector 기반 벡터 저장소
     */
    @Bean
    ChatClient ragChatClient(
            ChatClient.Builder builder,
            VectorStore vectorStore) {

        return builder
                // RAG에서 system 프롬프트는 '환각 방지 가드레일' 역할을 한다.
                // 검색된 Context만 근거로 삼고, 없으면 모른다고 말하게 강제해야
                // 모델이 사전학습 지식으로 임의 추측하는 것을 줄일 수 있다.
                .defaultSystem("""
                        제공된 문서 Context를 우선 근거로 답변하세요.
                        Context에 없는 내용은 추측하지 말고 모른다고 답변하세요.
                        """)
                .defaultAdvisors(
                        // QuestionAnswerAdvisor : RAG를 한 줄로 끝내주는 Advisor.
                        // 요청이 나가기 직전에
                        //   1) 사용자 질문을 임베딩하고
                        //   2) vectorStore에서 유사 문서 상위 K개를 검색해
                        //   3) 프롬프트에 Context 블록으로 끼워 넣는다.
                        //
                        // 검색 조건을 조정하려면 searchRequest(...)로 topK,
                        // 유사도 임계값(similarityThreshold), 메타데이터 필터를 지정한다.
                        // 예) .searchRequest(SearchRequest.builder().topK(4)
                        //         .similarityThreshold(0.5).build())
                        //
                        // 참고(Spring AI 2.0): 이 클래스는 별도 모듈
                        // 'spring-ai-vector-store-advisor'에 들어 있다(build.gradle 확인).
                        QuestionAnswerAdvisor
                                .builder(vectorStore)
                                .build()
                )
                .build();
    }

    /**
     * 데모용 지식 베이스 적재(seeding).
     *
     * <p>{@link CommandLineRunner}는 애플리케이션 기동이 끝난 직후 한 번 실행되는 훅이다.
     * 여기서 문서를 넣어야 검색할 대상이 생긴다.
     *
     * <p>{@code vectorStore.add()} 호출 시 내부적으로
     * 각 Document의 본문이 임베딩 API로 전송되어 벡터로 변환된 뒤 DB에 저장된다.
     * (즉 이 시점에 OpenAI 임베딩 호출 비용이 발생한다)
     *
     * <p><b>주의</b> : 이 예제는 실행할 때마다 같은 문서를 중복 저장한다.
     * 실무에서는 문서 ID를 지정하거나 적재 여부를 확인해 중복을 막는다.
     * 또한 실제 서비스에서는 긴 문서를 그대로 넣지 않고
     * {@code TokenTextSplitter} 등으로 <b>청크(chunk)</b>로 잘라 넣는다.
     * 청크가 너무 크면 관련 없는 내용이 섞이고, 너무 작으면 문맥이 끊긴다.
     */
    @Bean
    CommandLineRunner seedDocuments(VectorStore vectorStore) {
        return args -> vectorStore.add(List.of(
                // Document(본문, 메타데이터)
                // - 본문 : 임베딩되어 유사도 검색의 대상이 되는 텍스트
                // - 메타데이터 : 검색 후 필터링('category == spring-ai'만 조회 등)이나
                //                출처 표기에 사용된다. 임베딩 대상은 아니다.
                new Document(
                        "Spring AI의 ChatClient는 AI Chat Model과 통신하기 위한 Fluent API를 제공한다.",
                        Map.of("category", "spring-ai", "topic", "chatclient")),
                new Document(
                        "Advisor는 ChatClient 요청과 응답 처리 과정에 개입하여 Memory, RAG, Logging, Tool Calling 등의 기능을 조합할 수 있게 한다.",
                        Map.of("category", "spring-ai", "topic", "advisor")),
                new Document(
                        "RAG는 외부 지식 저장소에서 관련 문서를 검색하여 LLM의 Context에 추가하는 패턴이다.",
                        Map.of("category", "spring-ai", "topic", "rag")),
                new Document(
                        "Tool Calling은 LLM이 애플리케이션이 제공한 기능을 선택하고 호출하도록 하는 메커니즘이다.",
                        Map.of("category", "spring-ai", "topic", "tool"))
        ));
    }
}
