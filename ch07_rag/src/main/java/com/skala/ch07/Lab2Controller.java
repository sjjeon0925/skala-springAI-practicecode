package com.skala.ch07;

import java.io.IOException;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Lab2Controller {

    private final Lab2IngestService ingest;
    private final VectorStore vectorStore;
    private final Lab2RagService rag;
    private final double similarityThreshold;

    public Lab2Controller(Lab2IngestService ingest, VectorStore vectorStore, Lab2RagService rag,
            @Value("${lab2.similarity-threshold}") double similarityThreshold) {
        this.ingest = ingest;
        this.vectorStore = vectorStore;
        this.rag = rag;
        this.similarityThreshold = similarityThreshold;
    }

    public record Chunk(String source, Double score, String snippet) {}

    /**
     * classpath:/lab2-docs/ 아래의 샘플 문서를 모두 인제스트한다.
     * curl -X POST localhost:8080/lab2/ingest
     * 확인: POST /lab2/ingest → [{"source":"return-policy","chunks":7}, ...]
     */
    @PostMapping("/lab2/ingest")
    public List<Lab2IngestService.IngestResult> ingest() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/lab2-docs/*.md");
        return java.util.Arrays.stream(docs)
                .map(doc -> {
                    String filename = doc.getFilename();
                    String source = filename == null ? "unknown" : filename.replace(".md", "");
                    return ingest.ingest(doc, source, "v1");
                })
                .toList();
    }

    /** 답변을 만들기 전에 이것부터 만든다 — 검색 결과를 눈으로 확인한다. */
    @GetMapping("/lab2/retrieve")
    public List<Chunk> retrieve(@RequestParam String q,
            @RequestParam(defaultValue = "${lab2.top-k}") int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                        .query(q).topK(topK)
                        .similarityThreshold(similarityThreshold)     // 낮은 점수는 근거가 아니다
                        .build())
                .stream()
                .map(d -> new Chunk(d.getMetadata().get("source").toString(),
                        d.getScore(),                     // 점수를 노출한다
                        snippet(d.getText(), 120)))
                .toList();
    }

    /** curl 'localhost:8080/lab2/ask?q=단순 변심 반품은 며칠 이내인가요' */
    @GetMapping("/lab2/ask")
    public AnswerDto ask(@RequestParam String q) {
        return rag.ask(q);
    }

    private String snippet(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
