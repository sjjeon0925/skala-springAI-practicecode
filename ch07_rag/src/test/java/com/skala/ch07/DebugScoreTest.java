package com.skala.ch07;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;

@SpringBootTest
class DebugScoreTest {

    @Autowired
    Lab2IngestService ingest;

    @Autowired
    VectorStore vectorStore;

    @Test
    void printScores() throws Exception {
        Resource[] docs = new PathMatchingResourcePatternResolver().getResources("classpath:/lab2-docs/*.md");
        for (Resource doc : docs) {
            String source = doc.getFilename().replace(".md", "");
            var result = ingest.ingest(doc, source, "v1");
            System.out.println("ingested: " + result);
        }

        String q = "단순 변심 반품은 며칠 이내인가요";
        var hits = vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(10).build());
        System.out.println("hits (no threshold): " + hits.size());
        for (var d : hits) {
            System.out.printf("score=%.4f source=%s text=%s%n",
                    d.getScore(), d.getMetadata().get("source"),
                    d.getText().substring(0, Math.min(60, d.getText().length())));
        }
    }
}
