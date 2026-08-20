package com.skala.ch07;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Day 2 실습 Step4 — 골든 세트로 측정한다.
 *
 * <p>모델을 실제로 호출하므로 느리고 비용이 든다. 그래서 이 테스트는 별도로 돌린다.
 * {@code ./gradlew test --tests "com.skala.ch07.Lab2EvalTest"}
 */
@SpringBootTest
class Lab2EvalTest {

    private static final Logger log = LoggerFactory.getLogger(Lab2EvalTest.class);

    record Golden(String q, List<String> must, String src) {}

    @Autowired
    Lab2RagService service;

    @Autowired
    Lab2IngestService ingest;

    @Test
    void 골든_세트_평가() throws Exception {
        // 이 테스트는 별도의 스프링 컨텍스트(별도 인메모리 VectorStore)로 뜨므로
        // bootRun으로 이미 인제스트해 둔 것과 무관하게 여기서 다시 인제스트한다.
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/lab2-docs/*.md");
        for (Resource doc : docs) {
            String source = doc.getFilename() == null ? "unknown" : doc.getFilename().replace(".md", "");
            ingest.ingest(doc, source, "v1");
        }

        var mapper = new ObjectMapper();
        var golden = mapper.readValue(
                new ClassPathResource("golden.json").getInputStream(),
                new TypeReference<List<Golden>>() {});

        int pass = 0;
        for (Golden g : golden) {
            AnswerDto a = service.ask(g.q());

            boolean hit = g.must().stream().allMatch(k -> a.answer().contains(k));
            boolean cite = g.src() == null
                    || a.sources().stream().anyMatch(s -> s.contains(g.src()));

            if (hit && cite) {
                pass++;
                log.info("통과: {}\n  답변: {}\n  출처: {}", g.q(), a.answer(), a.sources());
            } else {
                log.warn("실패: {}\n  답변: {}\n  출처: {}  (hit={}, cite={})",
                        g.q(), a.answer(), a.sources(), hit, cite);
            }
        }

        log.info("통과 {}/{}", pass, golden.size());
        assertThat(pass).isGreaterThanOrEqualTo(8);
    }
}
