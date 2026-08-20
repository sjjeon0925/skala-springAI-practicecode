package com.skala.ch03;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ch03")
public class ChatClientController {

    private final HelloAiService service;

    public ChatClientController(HelloAiService service) {
        this.service = service;
    }

    /** curl 'localhost:8080/ch03/ask?q=Spring AI가 뭔가요' */
    @GetMapping("/summarize")
    public Map<String, String> summarize(@RequestParam String text) {
        return Map.of("summarized", service.summarize(text));
    }

    // @GetMapping("/ask-as")
    // public Map<String, String> askAs(@RequestParam String role, @RequestParam String q) {
    //     return Map.of("answer", service.askAs(role, q));
    // }

    @GetMapping("/ideating")
    public Map<String, String> ideating(@RequestParam String theme,
                                         @RequestParam String keywords) {
        return Map.of("ideas", service.ideating(theme, keywords));
    }
}
