package com.skala.helpdesk.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * 13장 Phase 4 — 데이터 접근 계층.
 *
 * <p>데모용 인메모리 저장소. 실제로는 JPA·MyBatis Repository가 이 자리에 온다.
 * 도구(OrderTools)는 이 계층을 통해서만 주문에 접근하고, 소유자 검증도 여기서 한다.
 */
@Repository
public class OrderRepository {

    private static final Map<String, Order> ORDERS = Map.of(
            "12345", new Order("12345", "user-1", "배송중", LocalDate.of(2026, 7, 30), "무선 이어폰"),
            "12346", new Order("12346", "user-1", "배송완료", LocalDate.of(2026, 7, 20), "USB-C 케이블"),
            "99999", new Order("99999", "user-2", "결제완료", LocalDate.of(2026, 8, 2), "노트북 스탠드"));

    public record Order(String id, String ownerId, String status, LocalDate eta, String item) {}

    /**
     * 소유자 검증 — 이 한 줄이 없으면 "주문번호 아무거나 대 보기"로 남의 주문이 조회된다.
     * RAG·Tool 보안 사고는 대부분 이 지점에서 난다.
     */
    public Optional<Order> findOwned(String orderId, String userId) {
        return Optional.ofNullable(ORDERS.get(orderId))
                .filter(o -> o.ownerId().equals(userId));
    }

    public List<Order> findRecentByOwner(String userId, int limit) {
        return ORDERS.values().stream()
                .filter(o -> o.ownerId().equals(userId))
                .sorted((a, b) -> b.eta().compareTo(a.eta()))
                .limit(limit)
                .toList();
    }
}
