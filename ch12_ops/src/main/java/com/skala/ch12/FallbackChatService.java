package com.skala.ch12;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 11장 — 재시도와 폴백.
 *
 * <p>공급자 장애는 실제로 일어난다. 폴백은 선택이 아니다.
 *
 * <p>재시도 대상은 <b>일시적 오류</b>(타임아웃·5xx·레이트 리밋)뿐이다.
 * 잘못된 요청(4xx)을 재시도하면 같은 실패를 돈 주고 반복할 뿐이다.
 */
@Service
public class FallbackChatService {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatService.class);

    private final ChatClient chat;

    @Value("${samples.fallback.model:gpt-4o-mini}")
    private String fallbackModel;

    public FallbackChatService(@Qualifier("supportClient") ChatClient chat) {
        this.chat = chat;
    }

    @Retryable(
            retryFor = { org.springframework.ai.retry.TransientAiException.class,
                         java.io.IOException.class },
            noRetryFor = { IllegalArgumentException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0))   // 1s → 2s → 4s
    public String ask(String question) {
        return chat.prompt().user(question).call().content();
    }

    /**
     * 재시도를 다 쓰고도 실패했을 때 호출된다.
     * 더 가볍고 안정적인 모델로 한 번 더 시도하고, 그것도 실패하면 사용자에게 정직하게 알린다.
     */
    @Recover
    public String fallback(Exception e, String question) {
        log.error("주 모델 호출 실패 — 폴백 모델로 전환한다", e);
        try {
            return chat.prompt()
                    .user(question)
                    .options(ChatOptions.builder().model(fallbackModel).build())
                    .call()
                    .content();

        } catch (Exception fallbackError) {
            log.error("폴백 모델도 실패", fallbackError);
            return "지금은 답변을 드리기 어렵습니다. 잠시 후 다시 시도해 주세요.";
        }
    }
}
