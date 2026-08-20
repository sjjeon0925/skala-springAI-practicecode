package com.skala.ch06;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ch06")
public class AdvancedController {

    private final WorkflowPatterns workflows;
    private final CachedChatService cached;

    public AdvancedController(WorkflowPatterns workflows, CachedChatService cached) {
        this.workflows = workflows;
        this.cached = cached;
    }

    @GetMapping("/route")
    public Map<String, String> route(@RequestParam String q) {
        return Map.of("answer", workflows.route(q));
    }

    @PostMapping("/summarize-all")
    public List<String> summarizeAll(@RequestBody List<String> documents) {
        return workflows.summarizeAll(documents);
    }

    @GetMapping("/write")
    public Map<String, String> write(@RequestParam String topic,
                                     @RequestParam(defaultValue = "2") int rounds) {
        return Map.of("result", workflows.writeWithReview(topic, rounds));
    }

    @GetMapping("/orchestrate")
    public Map<String, String> orchestrate(@RequestParam String goal) {
        return Map.of("result", workflows.orchestrate(goal));
    }

    /** 같은 질문을 두 번 던져 보면 두 번째가 즉시 돌아온다. */
    @GetMapping("/cached")
    public Map<String, Object> cachedAsk(@RequestParam String q) {
        String answer = cached.ask(q);
        return Map.of("answer", answer, "stats", cached.stats());
    }
}
