package com.skala.ch08;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 7장 — RAG 심화.
 *
 * <p>품질이 안 나올 때는 프롬프트를 고치기 전에 <b>검색 결과부터 눈으로 본다</b>.
 * 근거 안에 답이 아예 없으면 검색 문제이고, 근거에 있는데 못 쓰면 생성 문제다.
 * 고칠 곳이 완전히 다르다.
 */
@Service
public class AdvancedRagService {

    private static final Logger log = LoggerFactory.getLogger(AdvancedRagService.class);

    private final ChatClient chat;
    private final ChatClient strict;
    private final ChatClient.Builder builder;
    private final VectorStore vectorStore;

    public AdvancedRagService(@Qualifier("supportClient") ChatClient chat,
                              @Qualifier("extractClient") ChatClient strict,
                              ChatClient.Builder builder,
                              VectorStore vectorStore) {
        this.chat = chat;
        this.strict = strict;
        this.builder = builder;
        this.vectorStore = vectorStore;
    }

    // ══════════════════════════════════════════════ ① HyDE
    /**
     * HyDE — 질문이 아니라 "그럴듯한 가상 답변"으로 검색한다.
     * 질문과 문서는 문체가 다르지만, 답변과 문서는 문체가 비슷해서 더 잘 맞는다.
     */
    public List<Document> hydeSearch(String question, int topK) {
        String hypothetical = strict.prompt()
                .system("질문에 대한 그럴듯한 답변을 사실 여부와 무관하게 3문장으로 쓴다. 검색용 초안이다.")
                .user(question)
                .options(ChatOptions.builder().temperature(0.3).maxTokens(200).build())
                .call().content();

        log.debug("HyDE 가상 답변: {}", hypothetical);

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(hypothetical).topK(topK).build());
        return hits == null ? List.of() : hits;
    }

    // ══════════════════════════════════════════════ ② Re-ranking
    public record Ranking(List<Integer> indexes) {}

    /**
     * 넓게 찾고 좁게 넣는다 — 회수는 20건, 투입은 4건.
     * 벡터 유사도 상위가 곧 정답 순서는 아니다.
     */
    public List<Document> searchAndRerank(String question, int recall, int keep) {
        List<Document> candidates = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(recall).build());
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String numbered = IntStream.range(0, candidates.size())
                .mapToObj(i -> "[" + i + "] " + candidates.get(i).getText())
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse("");

        Ranking ranking = strict.prompt()
                .system("질문에 답하는 데 실제로 쓸모 있는 문단만 골라, 유용한 순서대로 번호를 나열한다.")
                .user("[질문]\n" + question + "\n\n[후보]\n" + numbered)
                .options(ChatOptions.builder().temperature(0.0).build())
                .call()
                .entity(Ranking.class);

        return ranking.indexes().stream()
                .filter(i -> i >= 0 && i < candidates.size())
                .distinct()
                .limit(keep)
                .map(candidates::get)
                .toList();
    }

    // ══════════════════════════════════════════════ ③ 모듈형 RAG
    /**
     * 질문 변환 · 질의 확장 · 검색을 각각 갈아 끼울 수 있는 형태.
     *
     * <p>주의: 변환·확장은 모델 호출을 추가로 쓴다(질의당 1~3회). 회수율이 실제로
     * 올라가는지 측정한 뒤 켜라.
     */
    public String modularRag(String question) {
        var advisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(builder.build().mutate())
                        .build())
                .queryExpander(MultiQueryExpander.builder()
                        .chatClientBuilder(builder.build().mutate())
                        .numberOfQueries(3)
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.6)
                        .topK(6)
                        .build())
                .build();

        return chat.prompt()
                .user(question)
                .advisors(advisor)
                .call().content();
    }

    // ══════════════════════════════════════════════ ④ Hybrid Search
    /**
     * 의미 검색 + 키워드 검색을 합친다.
     * 제품 코드("XR-2100")나 사번처럼 <b>정확히 일치해야 하는 토큰</b>은 벡터 검색이 약하다.
     *
     * <p>여기서는 개념 시연을 위해 간단한 RRF(Reciprocal Rank Fusion)를 직접 구현했다.
     * 운영에서는 검색 엔진(Elasticsearch·OpenSearch)의 하이브리드 기능을 쓰는 편이 낫다.
     */
    public List<Document> hybridSearch(String question, List<Document> keywordHits, int topK) {
        List<Document> semanticHits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK * 2).build());
        if (semanticHits == null) {
            semanticHits = List.of();
        }

        java.util.Map<String, Double> scores = new java.util.HashMap<>();
        java.util.Map<String, Document> byId = new java.util.HashMap<>();
        final int k = 60;   // RRF 상수 — 순위가 낮을수록 기여도가 완만히 준다

        for (int i = 0; i < semanticHits.size(); i++) {
            Document d = semanticHits.get(i);
            byId.put(d.getId(), d);
            scores.merge(d.getId(), 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < keywordHits.size(); i++) {
            Document d = keywordHits.get(i);
            byId.put(d.getId(), d);
            scores.merge(d.getId(), 1.0 / (k + i + 1), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(topK)
                .map(e -> byId.get(e.getKey()))
                .toList();
    }
}
