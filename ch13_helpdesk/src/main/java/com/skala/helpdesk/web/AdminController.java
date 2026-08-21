package com.skala.helpdesk.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.rag.IngestService;
import com.skala.helpdesk.tools.TicketTools;

/**
 * 13장 Phase 2·2심화·4 — 인제스트·승인 등 담당자용 API.
 *
 * <p>
 * 모델은 이 컨트롤러의 엔드포인트를 부를 수 없다 — 도구 목록에 없기 때문이다.
 * 승인·인제스트처럼 사람만 해야 하는 일은 이렇게 도구 바깥에 둔다.
 */
@RestController
public class AdminController {

    private final IngestService ingest;
    private final VectorStore vectorStore;
    private final TicketTools ticketTools;

    public AdminController(IngestService ingest, VectorStore vectorStore, TicketTools ticketTools) {
        this.ingest = ingest;
        this.vectorStore = vectorStore;
        this.ticketTools = ticketTools;
    }

    /** 규정 문서를 먼저 넣는다 — 안 하면 RAG 답변이 나오지 않는다. */
    @PostMapping("/ingest-samples")
    public List<IngestService.IngestResult> ingestSamples() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/docs/*.md");
        return Arrays.stream(docs)
                .map(doc -> ingest.ingest(doc, "handbook", "CS"))
                .toList();
    }

    /**
     * 인제스트 품질 확인 — 무엇이 들어갔는지 눈으로 본다(Phase 2 심화).
     * 성공 메시지가 아니라 실제 검색 결과로 확인해야 Phase 3에서 원인을 헤매지 않는다.
     */
    @GetMapping("/inspect")
    public List<Map<String, Object>> inspect(@RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        var hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(q).topK(topK).build());
        return hits.stream().map(d -> Map.<String, Object>of(
                "source", d.getMetadata().get("source"),
                "score", d.getScore(),
                "preview", d.getText().substring(0, Math.min(160, d.getText().length())))).toList();
    }

    /** 담당자 화면 — 대기 중인 환불 요청 목록. */
    @GetMapping("/admin/tickets/pending")
    public List<TicketTools.Approval> pending() {
        return ticketTools.pending();
    }

    @PostMapping("/admin/tickets/approve")
    public TicketTools.Approval approve(@RequestParam String id) {
        return ticketTools.approve(id);
    }
}
