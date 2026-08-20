package com.skala.helpdesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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
import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.rag.IngestService;

/**
 * 13장 Phase 8 — 골든 세트로 HelpDesk AI 품질 기준선을 잰다.
 *
 * <p>모델을 실제로 호출하므로 느리고 비용이 든다.
 * {@code ./gradlew test --tests "com.skala.helpdesk.eval.GoldenSetEvalTest"}
 */
@SpringBootTest
class GoldenSetEvalTest {

    private static final Logger log = LoggerFactory.getLogger(GoldenSetEvalTest.class);

    record Golden(String q, String userId, List<String> must) {}

    @Autowired
    HelpDeskService service;

    @Autowired
    IngestService ingest;

    @Test
    void 골든_세트_평가() throws IOException {
        // 이 테스트는 별도 스프링 컨텍스트(별도 인메모리 VectorStore)로 뜨므로 여기서 다시 인제스트한다.
        Resource[] docs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/docs/*.md");
        for (Resource doc : docs) {
            ingest.ingest(doc, "handbook", "CS");
        }

        var mapper = new ObjectMapper();
        var golden = mapper.readValue(
                new ClassPathResource("eval/golden.json").getInputStream(),
                new TypeReference<List<Golden>>() {});

        int pass = 0;
        for (int i = 0; i < golden.size(); i++) {
            Golden g = golden.get(i);
            // 문항마다 새 세션 — 앞 문항의 대화가 다음 문항에 섞이지 않게 한다
            AnswerDto a = service.ask(g.q(), "eval-" + i, g.userId());

            boolean hit = g.must().stream().allMatch(k -> a.answer().contains(k));
            if (hit) {
                pass++;
                log.info("통과: {}\n  답변: {}\n  출처: {}\n  도구사용: {}",
                        g.q(), a.answer(), a.sources(), a.toolUsed());
            } else {
                log.warn("실패: {}\n  답변: {}\n  출처: {}\n  도구사용: {}",
                        g.q(), a.answer(), a.sources(), a.toolUsed());
            }
        }

        log.info("통과 {}/{}", pass, golden.size());
        assertThat(pass).isGreaterThanOrEqualTo(6);
    }
}
