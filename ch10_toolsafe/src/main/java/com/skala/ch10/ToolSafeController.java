package com.skala.ch10;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ch10")
public class ToolSafeController {

    private final ChatClient chat;
    private final ApprovalTools approvalTools;

    public ToolSafeController(@Qualifier("supportClient") ChatClient chat,
                              ApprovalTools approvalTools) {
        this.chat = chat;
        this.approvalTools = approvalTools;
    }

    /** "주문 12345 환불해 주세요" → 실제 환불이 아니라 승인 요청이 접수된다. */
    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam String q,
                                   @RequestParam(defaultValue = "user-1") String userId) {
        String answer = chat.prompt()
                .user(q)
                .tools(approvalTools)
                .toolContext(Map.of("userId", userId))
                .call()
                .content();
        return Map.of("answer", answer);
    }

    /** 담당자 화면 — 대기 중인 승인 요청 목록. */
    @GetMapping("/approvals")
    public List<ApprovalTools.Approval> pending() {
        return approvalTools.pending();
    }

    @PostMapping("/approvals/approve")
    public ApprovalTools.Approval approve(@RequestParam String id) {
        return approvalTools.approve(id);
    }
}
