package com.skala.ch12;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ch12")
public class OpsController {

    private final FallbackChatService fallback;
    private final SemanticCacheService semanticCache;

    public OpsController(FallbackChatService fallback, SemanticCacheService semanticCache) {
        this.fallback = fallback;
        this.semanticCache = semanticCache;
    }

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam String q) {
        return Map.of("answer", fallback.ask(q));
    }

    /**
     * 뜻이 같은 두 질문을 차례로 던져 보면 두 번째가 캐시에서 나온다.
     *   1) /ch12/semantic?q=배송은 얼마나 걸리나요
     *   2) /ch12/semantic?q=배송 기간 알려주세요
     */
    @GetMapping("/semantic")
    public Map<String, Object> semantic(@RequestParam String q) {
        String answer = semanticCache.ask(q);
        return Map.of("answer", answer, "stats", semanticCache.stats());
    }
}
