package com.skala.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 13장 — 설정 외부화.
 *
 * <p>공급자·모델·임계값을 코드에 상수로 남기지 않고 {@code application.yml}로 뺀다.
 * 청크 크기·top-k·threshold 같은 값은 실험할 때마다 바뀌는데, 코드에 박아두면
 * 그때마다 다시 빌드해야 한다.
 *
 * <pre>
 *   helpdesk:
 *     rag:
 *       top-k: 4
 *       threshold: 0.3
 * </pre>
 */
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(Rag rag) {

    public record Rag(int topK, double threshold) {}
}
