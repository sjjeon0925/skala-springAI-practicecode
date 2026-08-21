package com.skala.helpdesk.web;

import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;

import reactor.core.publisher.Flux;

/**
 * 13장 Phase 6·7 — 상담 REST API.
 *
 * <p>
 * 업무 로직은 전부 {@link HelpDeskService}에 있다. 컨트롤러는 HTTP 파라미터를
 * 받아 서비스에 넘기고, 결과를 그대로 반환할 뿐이다.
 *
 * <p>
 * {@code userId}는 더 이상 쿼리 파라미터로 받지 않는다 — {@code SecurityConfig}가
 * 인증한 사용자({@link Principal})를 그대로 쓴다. 모델이 아무리 다른 사용자 이름을
 * 프롬프트에 써도 이 값은 못 바꾼다.
 */
@RestController
public class ChatController {

    private final HelpDeskService service;

    public ChatController(HelpDeskService service) {
        this.service = service;
    }

    /**
     * 상담 API — 세션 안에서 규정(RAG)·주문 조회(Tool)·환불 접수(승인 게이트)를 모두 다룬다.
     * Swagger의 "Authorize"로 user-1/pass 로그인 후 호출:
     * curl -u user-1:pass 'localhost:8080/chat?q=단순 변심 반품은 며칠 이내인가요&sessionId=s1'
     */
    @GetMapping("/chat")
    public AnswerDto chat(@RequestParam String q,
            @RequestParam(defaultValue = "demo") String sessionId,
            Principal principal) {
        return service.ask(q, sessionId, principal.getName());
    }

    /**
     * 상담 API — 스트리밍(SSE)판. 첫 글자가 빨리 온다.
     * curl -N -u user-1:pass 'localhost:8080/chat/stream?q=단순 변심 반품은 며칠
     * 이내인가요&sessionId=s1'
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String q,
            @RequestParam(defaultValue = "demo") String sessionId,
            Principal principal) {
        return service.stream(q, sessionId, principal.getName());
    }

    /** 대화 이력 확인 — 같은 sessionId로 이어 물었을 때 앞 대화가 실제로 기억되고 있는지 확인한다(Phase 5). */
    @GetMapping("/chat/history")
    public List<String> history(@RequestParam(defaultValue = "demo") String sessionId) {
        return service.history(sessionId);
    }

    @DeleteMapping("/chat/history")
    public void clearHistory(@RequestParam(defaultValue = "demo") String sessionId) {
        service.clearHistory(sessionId);
    }
}
