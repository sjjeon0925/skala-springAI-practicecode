package com.skala.ch04;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

/**
 * 3장 — 스트리밍.
 *
 * <p>스트리밍은 "끝을 스스로 챙겨야 하는" 호출이다. 사용자가 창을 닫아도
 * 서버 쪽 구독이 살아 있으면 모델 호출은 끝까지 진행되고 그대로 비용이 된다.
 */
@Service
public class StreamingService {

    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);

    private final ChatClient chat;

    public StreamingService(@Qualifier("supportClient") ChatClient chat) {
        this.chat = chat;
    }

    public Flux<String> stream(String question) {
        AtomicInteger chunks = new AtomicInteger();
        long startedAt = System.nanoTime();

        return chat.prompt()
                .user(question)
                .stream()
                .content()
                // 전체 상한 — 무한정 기다리지 않는다
                .timeout(Duration.ofSeconds(60))
                // 첫 토큰까지의 시간(TTFB)은 체감 성능을 좌우한다. 따로 잰다.
                .doOnNext(token -> {
                    if (chunks.getAndIncrement() == 0) {
                        log.info("첫 토큰까지 {} ms", (System.nanoTime() - startedAt) / 1_000_000);
                    }
                })
                // 브라우저 이탈 — 구독이 취소되면 상류 호출도 정리된다
                .doOnCancel(() -> log.info("클라이언트 취소 — 스트림 종료 (수신 {} 조각)", chunks.get()))
                .onErrorResume(e -> {
                    log.error("스트리밍 실패", e);
                    return Flux.just("\n\n(일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.)");
                })
                .doFinally(signal -> log.info("스트림 종료 signal={} chunks={} 총 {} ms",
                        signal, chunks.get(), (System.nanoTime() - startedAt) / 1_000_000));
    }
}
