package com.skala.ch07;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ch07")
public class RagController {

    private final IngestService ingest;
    private final RagService rag;

    public RagController(IngestService ingest, RagService rag) {
        this.ingest = ingest;
        this.rag = rag;
    }

    /**
     * classpath:/docs/ 아래의 샘플 문서를 모두 인제스트한다.
     * curl -X POST localhost:8080/ch07/ingest-samples
     */
    @PostMapping("/ingest-samples")
    public List<IngestService.IngestResult> ingestSamples() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/docs/*.md");
        return java.util.Arrays.stream(docs)
                .map(doc -> ingest.ingest(doc, "handbook", "CS"))
                .toList();
    }

    /** curl -F 'file=@규정.pdf' 'localhost:8080/ch07/ingest?docType=policy&dept=CS' */
    @PostMapping("/ingest")
    public IngestService.IngestResult ingestUpload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "handbook") String docType,
                                                   @RequestParam(defaultValue = "CS") String dept)
            throws IOException {
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        return ingest.ingest(resource, docType, dept);
    }

    /** curl 'localhost:8080/ch07/ask?q=반품 규정 알려줘' */
    @GetMapping("/ask")
    public RagService.Answer ask(@RequestParam String q,
                                 @RequestParam(required = false) String dept,
                                 @RequestParam(required = false) String sessionId) {
        return rag.ask(q, dept, sessionId);
    }

    /** 검색만 해 본다 — RAG 품질 문제의 원인이 검색인지 생성인지 가르는 첫 단계. */
    @GetMapping("/retrieve")
    public Map<String, Object> retrieve(@RequestParam String q,
                                        @RequestParam(defaultValue = "5") int topK) {
        return Map.of("query", q, "chunks", rag.retrieveOnly(q, topK));
    }
}
