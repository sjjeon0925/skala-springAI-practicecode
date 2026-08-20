package com.skala.ch04;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ch04")
public class PromptController {

    private final PromptService prompts;
    private final StreamingService streaming;

    public PromptController(PromptService prompts, StreamingService streaming) {
        this.prompts = prompts;
        this.streaming = streaming;
    }

    @PostMapping(value = "/structured", consumes = "text/plain")
    public Map<String, String> structured(@RequestBody String article) {
        return Map.of("result", prompts.structuredPrompt(article));
    }

    /** curl 'localhost:8080/ch04/classify?q=결제가 두 번 됐어요' → BILLING */
    @GetMapping("/classify")
    public Map<String, String> classify(@RequestParam String q) {
        return Map.of("label", prompts.classifyWithExamples(q));
    }

    @GetMapping("/reason")
    public Map<String, String> reason(@RequestParam String q) {
        return Map.of("answer", prompts.reasonThenAnswer(q));
    }

    @PostMapping(value = "/review", consumes = "text/plain")
    public Map<String, String> review(@RequestBody String code,
                                      @RequestParam(defaultValue = "Java") String lang) {
        return Map.of("review", prompts.reviewCode(code, lang));
    }

    /** curl -N 'localhost:8080/ch04/stream?q=Spring AI를 소개해줘' */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String q) {
        return streaming.stream(q);
    }
}
