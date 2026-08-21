package com.skala.helpdesk.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.repository.OrderRepository;

/**
 * 13장 Phase 4 — Tool Calling(주문 조회).
 *
 * <p>도구를 만들 때의 세 원칙.
 * <ol>
 *   <li>description 이 곧 모델에게 주는 사용 설명서다. 대충 쓰면 엉뚱하게 부른다.</li>
 *   <li>예외를 던지지 말고 사람이 읽을 메시지를 반환한다 — 대화 전체가 실패하지 않는다.</li>
 *   <li>권한 검증은 도구 <b>안에서</b> 한다. 모델이 넘긴 ID 를 믿지 않는다.</li>
 * </ol>
 *
 * <p>실제 데이터 접근은 {@link OrderRepository}에 맡긴다 — 도구는 "무엇을 묻는지"에만 집중한다.
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private final OrderRepository orders;

    public OrderTools(OrderRepository orders) {
        this.orders = orders;
    }

    @Tool(description = "주문번호로 배송 상태와 예상 도착일을 조회한다. 사용자 본인의 주문만 조회된다.")
    public String orderStatus(
            @ToolParam(description = "주문번호(숫자 5자리)") String orderId,
            ToolContext context) {

        String userId = currentUser(context);
        log.info("[TOOL] orderStatus orderId={} by={}", orderId, userId);

        return orders.findOwned(orderId, userId)
                .map(o -> "주문 %s · 품목 %s · 상태 %s · 예상도착 %s"
                        .formatted(o.id(), o.item(), o.status(), o.eta()))
                // 없는 주문과 남의 주문을 구분해 알려 주면 그 자체가 정보 노출이다
                .orElse("해당 주문을 찾을 수 없습니다.");
    }

    @Tool(description = "사용자의 최근 주문 목록을 조회한다. 최대 5건까지 반환한다.")
    public String recentOrders(ToolContext context) {
        String userId = currentUser(context);
        log.info("[TOOL] recentOrders by={}", userId);

        List<OrderRepository.Order> mine = orders.findRecentByOwner(userId, 5);
        if (mine.isEmpty()) {
            return "조회된 주문이 없습니다.";
        }
        return mine.stream()
                .map(o -> "- %s / %s / %s".formatted(o.id(), o.item(), o.status()))
                .reduce("최근 주문 %d건:".formatted(mine.size()), (a, b) -> a + "\n" + b);
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
