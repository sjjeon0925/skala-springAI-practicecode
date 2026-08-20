package com.skala.ch11;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 10장 — 토큰 사용량 Advisor.
 *
 * <p>보이지 않는 비용은 줄일 수도 없다. 모든 호출을 한 곳에서 계측하면
 * "어느 기능이 돈을 쓰는가"가 대시보드에서 바로 보인다.
 *
 * <p>확인: {@code GET /actuator/metrics/ai.tokens} · {@code /actuator/prometheus}
 */
@Component
public class TokenMeterAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenMeterAdvisor.class);

    private final MeterRegistry registry;

    public TokenMeterAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        long elapsedNanos = System.nanoTime() - started;

        registry.timer("ai.latency").record(elapsedNanos, TimeUnit.NANOSECONDS);

        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            Usage usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                registry.counter("ai.tokens", "type", "prompt")
                        .increment(nullSafe(usage.getPromptTokens()));
                registry.counter("ai.tokens", "type", "completion")
                        .increment(nullSafe(usage.getCompletionTokens()));

                log.debug("토큰 prompt={} completion={} 지연={}ms",
                        usage.getPromptTokens(), usage.getCompletionTokens(),
                        elapsedNanos / 1_000_000);
            }
        }
        return response;
    }

    private double nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    @Override
    public String getName() {
        return "tokenMeter";
    }

    /** 낮을수록 바깥 — 계측은 바깥쪽에 둬야 안쪽 전체 시간이 잡힌다. */
    @Override
    public int getOrder() {
        return 10;
    }
}
