package com.skala.ch08;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ch08")
public class RagAdvancedController {

    private final AdvancedRagService service;
    private final IngestService ingest;

    public RagAdvancedController(AdvancedRagService service, IngestService ingest) {
        this.service = service;
        this.ingest = ingest;
    }

    /**
     * 검색할 것이 있어야 심화 기법을 비교할 수 있다 — 먼저 이것부터 부른다.
     * curl -X POST localhost:8080/ch08/ingest-samples
     */
    @PostMapping("/ingest-samples")
    public List<IngestService.IngestResult> ingestSamples() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/docs/*.md");
        return Arrays.stream(docs)
                .map(doc -> ingest.ingest(doc, "handbook", "CS"))
                .toList();
    }

    /** 일반 검색과 비교해 보면 HyDE 의 효과를 눈으로 확인할 수 있다. */
    @GetMapping("/hyde")
    public List<String> hyde(@RequestParam String q,
                             @RequestParam(defaultValue = "5") int topK) {
        return service.hydeSearch(q, topK).stream().map(Document::getText).toList();
    }

    /** 넓게 찾고(recall) 좁게 넣는다(keep). */
    @GetMapping("/rerank")
    public Map<String, Object> rerank(@RequestParam String q,
                                      @RequestParam(defaultValue = "20") int recall,
                                      @RequestParam(defaultValue = "4") int keep) {
        List<Document> kept = service.searchAndRerank(q, recall, keep);
        return Map.of("recall", recall, "kept", kept.size(),
                "chunks", kept.stream().map(Document::getText).toList());
    }

    @GetMapping("/modular")
    public Map<String, String> modular(@RequestParam String q) {
        return Map.of("answer", service.modularRag(q));
    }
}
