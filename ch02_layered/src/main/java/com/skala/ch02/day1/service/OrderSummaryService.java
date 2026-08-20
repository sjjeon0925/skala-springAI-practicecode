package com.skala.ch02.day1.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.ch02.day1.web.SummaryResponse;
import com.skala.ch02.domain.Order;
import com.skala.ch02.repository.OrderRepository;
import com.skala.ch02.service.OrderNotFoundException;

/**
 * Day1 실습 — 1장에서 만든 계층(OrderRepository)을 그대로 쓴다.
 * 주문을 못 찾으면 모델을 부르지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class OrderSummaryService {

    private final OrderRepository orders;
    private final ChatClient summaryChat;

    public OrderSummaryService(OrderRepository orders, ChatClient summaryChatClient) {
        this.orders = orders;
        this.summaryChat = summaryChatClient;
    }

    public SummaryResponse summarize(String orderId, String userId) {
        Order order = orders.findByIdAndOwnerId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        String summary = summaryChat.prompt()
                .user(u -> u.text("주문번호 {id} · 상품 {item} · 상태 {status} · 도착예정 {eta}"
                                + "\n위 정보를 한 문장으로 요약해 줘.")
                        .param("id", order.getId())
                        .param("item", order.getItem())
                        .param("status", order.getStatus().label())
                        .param("eta", order.getEta()))
                .call().content();

        return new SummaryResponse(order.getId(), summary);
    }
}
