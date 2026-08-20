package com.skala.ch09;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ch09")
public class ToolsController {

    private final ToolChatService service;

    public ToolsController(ToolChatService service) {
        this.service = service;
    }

    /**
     * 세 가지를 차례로 던져 보면 도구 호출 판단을 눈으로 확인할 수 있다.
     *   1) 안녕하세요            → 도구 호출 없음
     *   2) 서울 날씨 어때?        → currentWeather 호출
     *   3) 주문 12345 어디쯤이야? → orderStatus 호출
     *   4) 주문 99999 알려줘      → 남의 주문 → "찾을 수 없습니다"
     */
    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam String q,
                                   @RequestParam(defaultValue = "user-1") String userId) {
        return Map.of("answer", service.ask(q, userId));
    }

    @GetMapping("/ask-verbose")
    public Map<String, Object> askVerbose(@RequestParam String q,
                                          @RequestParam(defaultValue = "user-1") String userId) {
        return service.askVerbose(q, userId);
    }
}
