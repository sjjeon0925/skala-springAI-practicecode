package com.skala.ch10;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 9장 — 승인 게이트(Human-in-the-loop).
 *
 * <p>자율성의 크기는 <b>되돌릴 수 있는 정도</b>에 맞춘다.
 * 조회는 자유롭게, 쓰기는 제한적으로, 되돌릴 수 없는 일은 사람의 승인을 거쳐.
 *
 * <p>핵심은 도구가 <b>실행하지 않고 접수만</b> 한다는 것이다.
 * 모델에게는 "접수됐다"고 알려 주면 대화는 자연스럽게 이어진다.
 */
@Component
public class ApprovalTools {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTools.class);

    private final Map<String, Approval> approvals = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    public record Approval(String id, String type, String targetId, String reason,
                           String requestedBy, Instant requestedAt, String status) {}

    @Tool(description = """
            환불을 요청한다. 이 도구는 요청을 접수만 하며 실제 환불은 담당자 승인 후 처리된다.
            사용자가 명시적으로 환불을 요청했을 때만 부른다.""")
    public String requestRefund(
            @ToolParam(description = "주문번호") String orderId,
            @ToolParam(description = "환불 사유(사용자가 말한 그대로)") String reason,
            ToolContext context) {

        String userId = String.valueOf(context.getContext().get("userId"));
        String id = "AP-" + sequence.incrementAndGet();

        approvals.put(id, new Approval(id, "REFUND", orderId, reason,
                userId, Instant.now(), "PENDING"));

        // 감사 로그 — 누가·언제·무엇을 요청했는지가 남아야 되돌아볼 수 있다
        log.warn("[APPROVAL] REFUND 요청 id={} order={} by={} reason={}", id, orderId, userId, reason);

        return "환불 요청 %s 번으로 접수했습니다. 담당자 승인 후 1~3영업일 내 처리됩니다.".formatted(id);
    }

    @Tool(description = "접수된 요청의 처리 상태를 조회한다.")
    public String approvalStatus(@ToolParam(description = "요청 번호(예: AP-1001)") String approvalId) {
        Approval a = approvals.get(approvalId);
        return a == null
                ? "해당 요청 번호를 찾을 수 없습니다."
                : "요청 %s · 유형 %s · 상태 %s".formatted(a.id(), a.type(), a.status());
    }

    // ── 담당자용(모델이 아니라 사람이 쓰는 API) ─────────────────
    public List<Approval> pending() {
        return approvals.values().stream()
                .filter(a -> "PENDING".equals(a.status()))
                .toList();
    }

    public Approval approve(String id) {
        return approvals.computeIfPresent(id, (k, a) -> new Approval(
                a.id(), a.type(), a.targetId(), a.reason(), a.requestedBy(), a.requestedAt(), "APPROVED"));
    }
}
