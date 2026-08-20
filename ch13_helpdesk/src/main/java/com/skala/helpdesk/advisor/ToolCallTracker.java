package com.skala.helpdesk.advisor;

import org.springframework.stereotype.Component;

/**
 * 13장 — 이번 요청에서 도구가 실제로 불렸는지 기록한다.
 *
 * <p>{@code ToolAuditAspect}가 매 {@code @Tool} 호출마다 {@link #mark()}를 남기고,
 * 컨트롤러가 요청 처리 전후로 {@link #reset()}·{@link #wasCalled()}를 호출해
 * {@code AnswerDto.toolUsed}를 채운다. 요청 하나는 같은 스레드에서 끝까지 처리되므로
 * ThreadLocal로 충분하다.
 */
@Component
public class ToolCallTracker {

    private final ThreadLocal<Boolean> called = ThreadLocal.withInitial(() -> false);

    public void mark() {
        called.set(true);
    }

    public boolean wasCalled() {
        return called.get();
    }

    public void reset() {
        called.remove();
    }
}
