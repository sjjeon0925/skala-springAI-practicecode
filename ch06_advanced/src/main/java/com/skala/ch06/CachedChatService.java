package com.skala.ch06;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 5장 — 호출 최적화: 완전히 같은 질문은 다시 묻지 않는다.
 *
 * <p>가장 값싼 최적화는 "호출하지 않는 것"이다. 다만 캐시해도 되는 질문인지가 먼저다.
 * 개인화되거나 실시간 데이터가 섞인 답변은 캐시하면 안 된다.
 */
@Service
public class CachedChatService {

    private static final Logger log = LoggerFactory.getLogger(CachedChatService.class);
    private static final Duration TTL = Duration.ofHours(6);

    private final ChatClient chat;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public CachedChatService(@Qualifier("supportClient") ChatClient chat) {
        this.chat = chat;
    }

    private record Entry(String answer, Instant storedAt) {
        boolean expired() {
            return Instant.now().isAfter(storedAt.plus(TTL));
        }
    }

    public String ask(String question) {
        String key = normalize(question);

        Entry cached = cache.get(key);
        if (cached != null && !cached.expired()) {
            hits.incrementAndGet();
            log.debug("캐시 적중 — 호출 생략");
            return cached.answer();
        }

        misses.incrementAndGet();
        String answer = chat.prompt().user(question).call().content();
        cache.put(key, new Entry(answer, Instant.now()));
        return answer;
    }

    /** 공백·대소문자·문장부호 차이는 같은 질문으로 본다. */
    private String normalize(String question) {
        return question.trim().toLowerCase().replaceAll("[\\s?!.]+", " ");
    }

    public Map<String, Long> stats() {
        long h = hits.get();
        long m = misses.get();
        return Map.of("hits", h, "misses", m, "hitRatePercent", h + m == 0 ? 0 : h * 100 / (h + m));
    }

    public void clear() {
        cache.clear();
    }
}
