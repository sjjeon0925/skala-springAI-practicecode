package com.skala.ch12;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 11장 — 시맨틱 캐시.
 *
 * <p>"배송 얼마나 걸려요"와 "배송기간 알려주세요"는 문자열로는 다르지만 뜻은 같다.
 * 질문을 임베딩해 유사한 과거 질문이 있으면 그 답을 재사용한다.
 *
 * <p><b>두 가지 주의</b>
 * <ul>
 *   <li>임계값을 낮추면 다른 질문에 엉뚱한 답이 나간다. 0.95 이상에서 시작한다.</li>
 *   <li>개인화·실시간 데이터가 섞인 답변("내 주문 상태")은 절대 캐시하지 않는다.</li>
 * </ul>
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);
    private static final double HIT_THRESHOLD = 0.95;

    private final ChatClient chat;
    private final VectorStore cacheStore;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public SemanticCacheService(@Qualifier("supportClient") ChatClient chat,
                                EmbeddingModel embeddingModel) {
        this.chat = chat;
        // 본문 검색용 스토어와 섞이지 않도록 캐시 전용 스토어를 따로 둔다
        this.cacheStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    public String ask(String question) {
        Optional<String> cached = lookup(question);
        if (cached.isPresent()) {
            hits.incrementAndGet();
            log.info("시맨틱 캐시 적중 — 모델 호출 생략");
            return cached.get();
        }

        misses.incrementAndGet();
        String answer = chat.prompt().user(question).call().content();
        put(question, answer);
        return answer;
    }

    public Optional<String> lookup(String question) {
        List<Document> hitDocs = cacheStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(1)
                .similarityThreshold(HIT_THRESHOLD)
                .build());

        if (hitDocs == null || hitDocs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) hitDocs.get(0).getMetadata().get("answer"));
    }

    public void put(String question, String answer) {
        cacheStore.add(List.of(new Document(question, Map.of(
                "answer", answer,
                "cachedAt", Instant.now().toString()))));
    }

    public Map<String, Long> stats() {
        long h = hits.get();
        long m = misses.get();
        return Map.of("hits", h, "misses", m,
                "hitRatePercent", h + m == 0 ? 0 : h * 100 / (h + m));
    }
}
