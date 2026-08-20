package com.skala.ch09;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 8장 — Tool Calling.
 *
 * <p>도구를 만들 때의 세 원칙.
 * <ol>
 *   <li>description 이 곧 모델에게 주는 사용 설명서다. 대충 쓰면 엉뚱하게 부른다.</li>
 *   <li>예외를 던지지 말고 사람이 읽을 메시지를 반환한다 — 대화 전체가 실패하지 않는다.</li>
 *   <li>권한 검증은 도구 <b>안에서</b> 한다. 모델이 넘긴 ID 를 믿지 않는다.</li>
 * </ol>
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    /** 데모용 인메모리 주문 데이터. 실제로는 리포지토리를 주입한다. */
    private static final Map<String, Order> ORDERS = Map.of(
            "12345", new Order("12345", "user-1", "배송중", LocalDate.of(2026, 7, 30), "무선 이어폰"),
            "12346", new Order("12346", "user-1", "배송완료", LocalDate.of(2026, 7, 20), "USB-C 케이블"),
            "99999", new Order("99999", "user-2", "결제완료", LocalDate.of(2026, 8, 2), "노트북 스탠드"));

    public record Order(String id, String ownerId, String status, LocalDate eta, String item) {}

    @Tool(description = "주문번호로 배송 상태와 예상 도착일을 조회한다. 사용자 본인의 주문만 조회된다.")
    public String orderStatus(
            @ToolParam(description = "주문번호(숫자 5자리)") String orderId,
            ToolContext context) {

        String userId = currentUser(context);
        log.info("[TOOL] orderStatus orderId={} by={}", orderId, userId);

        return findOwned(orderId, userId)
                .map(o -> "주문 %s · 품목 %s · 상태 %s · 예상도착 %s"
                        .formatted(o.id(), o.item(), o.status(), o.eta()))
                // 없는 주문과 남의 주문을 구분해 알려 주면 그 자체가 정보 노출이다
                .orElse("해당 주문을 찾을 수 없습니다.");
    }

    @Tool(description = "사용자의 최근 주문 목록을 조회한다. 최대 5건까지 반환한다.")
    public String recentOrders(ToolContext context) {
        String userId = currentUser(context);
        log.info("[TOOL] recentOrders by={}", userId);

        List<Order> mine = ORDERS.values().stream()
                .filter(o -> o.ownerId().equals(userId))
                .sorted((a, b) -> b.eta().compareTo(a.eta()))
                .limit(5)
                .toList();

        if (mine.isEmpty()) {
            return "조회된 주문이 없습니다.";
        }
        return mine.stream()
                .map(o -> "- %s / %s / %s".formatted(o.id(), o.item(), o.status()))
                .reduce("최근 주문 %d건:".formatted(mine.size()), (a, b) -> a + "\n" + b);
    }

    /**
     * 소유자 검증 — 이 한 줄이 없으면 "주문번호 아무거나 대 보기"로 남의 주문이 조회된다.
     * RAG·Tool 보안 사고는 대부분 이 지점에서 난다.
     */
    private Optional<Order> findOwned(String orderId, String userId) {
        return Optional.ofNullable(ORDERS.get(orderId))
                .filter(o -> o.ownerId().equals(userId));
    }

    /** 사용자 ID 는 프롬프트가 아니라 ToolContext 로 온다 — 모델이 바꿔 부를 수 없는 경로다. */
    private String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("toolContext 에 userId 가 없다 — 호출부 설정을 확인하라");
        }
        return userId.toString();
    }
}
