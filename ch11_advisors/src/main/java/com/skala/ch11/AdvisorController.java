package com.skala.ch11;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;

/**
 * 12장 — Advisor 체인을 눈으로 확인하는 엔드포인트.
 *
 * <p>{@code MemoryChatConfig} 가 조립한 {@code assistantClient} 하나만 쓴다.
 * 컨트롤러에는 RAG·메모리·안전 필터·계측 코드가 한 줄도 없다 —
 * <b>전부 Advisor 체인에 들어 있다</b>는 것이 이 장의 요지다.
 *
 * <p>순서대로 던져 보면 Advisor 가 각각 어디서 끼어드는지 보인다.
 * <ol>
 *   <li>{@code POST /ch11/ingest-samples} — QA Advisor 가 쓸 근거 문서를 넣는다</li>
 *   <li>{@code /ch11/chat?q=단순 변심 반품은 며칠 이내인가요} — 근거로 답한다(QA)</li>
 *   <li>같은 sessionId 로 {@code ?q=그럼 왕복 배송비는?} — 앞 대화를 기억한다(Memory)</li>
 *   <li>{@code ?q=내 주민등록번호 알려줘} — 모델에 닿기 전에 차단된다(SafeGuard)</li>
 *   <li>{@code /ch11/metrics} — 위 호출들의 토큰이 쌓여 있다(TokenMeter)</li>
 * </ol>
 */
@RestController
@RequestMapping("/ch11")
public class AdvisorController {

    private final ChatClient assistant;
    private final IngestService ingest;
    private final MeterRegistry registry;

    public AdvisorController(@Qualifier("assistantClient") ChatClient assistant,
                             IngestService ingest,
                             MeterRegistry registry) {
        this.assistant = assistant;
        this.ingest = ingest;
        this.registry = registry;
    }

    /** QA Advisor 가 참고할 근거 문서를 넣는다. 이걸 안 하면 "확인되지 않습니다"만 나온다. */
    @PostMapping("/ingest-samples")
    public List<IngestService.IngestResult> ingestSamples() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/docs/*.md");
        return Arrays.stream(docs)
                .map(doc -> ingest.ingest(doc, "handbook", "CS"))
                .toList();
    }

    /**
     * sessionId 가 곧 대화 단위다. 같은 값으로 이어 물으면 앞 내용을 기억하고,
     * 값을 바꾸면 처음 만난 것처럼 답한다.
     */
    @GetMapping("/chat")
    public Map<String, String> chat(@RequestParam String q,
                                    @RequestParam(defaultValue = "demo") String sessionId) {
        String answer = assistant.prompt()
                .user(q)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        return Map.of("answer", answer, "sessionId", sessionId);
    }

    /**
     * TokenMeterAdvisor 가 쌓은 값. {@code /actuator/metrics/ai.tokens} 와 같은 수치를
     * 보기 좋게 꺼낸 것뿐이다 — 대시보드에서는 Prometheus 로 긁어 간다.
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of(
                "promptTokens", counter("ai.tokens", "type", "prompt"),
                "completionTokens", counter("ai.tokens", "type", "completion"),
                "calls", Search.in(registry).name("ai.latency").timer() == null
                        ? 0L
                        : Search.in(registry).name("ai.latency").timer().count(),
                "hint", "원본 지표: /actuator/metrics/ai.tokens · /actuator/prometheus");
    }

    private double counter(String name, String tagKey, String tagValue) {
        var c = Search.in(registry).name(name).tag(tagKey, tagValue).counter();
        return c == null ? 0d : c.count();
    }
}
