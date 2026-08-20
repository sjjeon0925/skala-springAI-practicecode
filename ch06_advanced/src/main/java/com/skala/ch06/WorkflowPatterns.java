package com.skala.ch06;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * 5장 — LLM 워크플로 패턴.
 *
 * <p>복잡한 일을 한 번의 거대한 프롬프트로 밀어붙이면 정확도가 떨어진다.
 * 작게 쪼개 이어 붙이고(Chaining), 나눠 동시에 처리하고(Parallel),
 * 유형별로 갈라(Routing) 보내고, 필요하면 평가-교정 루프를 건다.
 */
@Service
public class WorkflowPatterns {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPatterns.class);

    private final ChatClient chat;
    private final ChatClient strict;
    private final Executor aiExecutor;

    public WorkflowPatterns(@Qualifier("supportClient") ChatClient chat,
                            @Qualifier("extractClient") ChatClient strict,
                            @Qualifier("aiExecutor") Executor aiExecutor) {
        this.chat = chat;
        this.strict = strict;
        this.aiExecutor = aiExecutor;
    }

    // ══════════════════════════════════════════════ ① Routing
    public enum Route { SIMPLE_FAQ, TECHNICAL, COMPLAINT }

    public record Classification(Route route, String reason) {}

    /**
     * 분류기가 값싼 모델로 유형을 정하고, 유형별 전용 경로로 보낸다.
     * 모든 질문에 비싼 모델을 쓰지 않아도 되게 만드는 가장 실용적인 패턴이다.
     */
    public String route(String question) {
        Classification c = strict.prompt()
                .user("""
                        다음 문의를 분류하라.
                          SIMPLE_FAQ  : 규정·안내로 답할 수 있는 단순 질문
                          TECHNICAL   : 원인 분석·단계적 추론이 필요한 기술 질문
                          COMPLAINT   : 불만·항의가 섞인 문의
                        ---
                        """ + question)
                .call()
                .entity(Classification.class);

        log.info("라우팅 결과 {} — {}", c.route(), c.reason());

        return switch (c.route()) {
            case SIMPLE_FAQ -> chat.prompt()
                    .user(question)
                    .options(ChatOptions.builder().temperature(0.2).maxTokens(300).build())
                    .call().content();

            case TECHNICAL -> chat.prompt()
                    .system("단계적으로 원인을 좁혀 가며 분석하되, 최종 출력은 결론과 근거 3개만 쓴다.")
                    .user(question)
                    .call().content();

            case COMPLAINT -> chat.prompt()
                    .system("먼저 공감을 표현하고, 사실만 안내하며, 마지막에 담당자 연결을 제안한다.")
                    .user(question)
                    .call().content();
        };
    }

    // ══════════════════════════════════════════════ ② Parallelization
    /**
     * 서로 의존하지 않는 작업을 동시에 호출한다.
     * 지연은 줄지만 <b>비용은 줄지 않는다</b> — 호출 수는 그대로다.
     * 전용 풀로 동시 호출 수를 묶지 않으면 곧 레이트 리밋(429)을 만난다.
     */
    public List<String> summarizeAll(List<String> documents) {
        List<CompletableFuture<String>> futures = documents.stream()
                .map(doc -> CompletableFuture
                        .supplyAsync(() -> chat.prompt()
                                .user("다음 문서를 3문장으로 요약하라.\n---\n" + doc)
                                .call().content(), aiExecutor)
                        .exceptionally(e -> {
                            log.warn("요약 실패", e);
                            return "(요약 실패)";
                        }))
                .toList();

        return futures.stream()
                .map(f -> f.completeOnTimeout("(시간 초과)", 30, TimeUnit.SECONDS))
                .map(CompletableFuture::join)
                .toList();
    }

    // ══════════════════════════════════════════════ ③ Evaluator-Optimizer
    public record Review(boolean passed, String feedback) {}

    /**
     * 생성 → 평가 → 재생성을 정해진 횟수만 반복한다.
     * 평가자에게는 <b>다른 시스템 프롬프트</b>를 줘야 한다 — 같은 관점이면 자기 글을 통과시킨다.
     */
    public String writeWithReview(String topic, int maxRounds) {
        String draft = chat.prompt()
                .user("다음 주제로 500자 내외의 초안을 써라: " + topic)
                .call().content();

        for (int round = 1; round <= maxRounds; round++) {
            Review review = strict.prompt()
                    .system("""
                            너는 엄격한 편집자다. 아래 기준으로만 판정한다.
                              - 주장마다 근거가 붙어 있는가
                              - 중복되는 문장이 없는가
                              - 과장·단정 표현이 없는가
                            통과 여부(passed)와 개선점(feedback)을 낸다.""")
                    .user("초안:\n" + draft)
                    .call()
                    .entity(Review.class);

            if (review.passed()) {
                log.info("{}회차에서 통과", round);
                return draft;
            }

            log.info("{}회차 재작성 — {}", round, review.feedback());
            draft = chat.prompt()
                    .user("아래 지적을 반영해 다시 써라.\n[지적]\n" + review.feedback()
                            + "\n\n[초안]\n" + draft)
                    .call().content();
        }
        return draft;   // 반복 상한 도달 — 무한 루프는 곧 비용 사고다
    }

    // ══════════════════════════════════════════════ ④ Orchestrator-Workers
    public record Plan(List<String> subtasks) {}

    /**
     * 오케스트레이터가 할 일을 쪼개고, 워커들이 각각 처리하고, 마지막에 합친다.
     * 작업 개수를 미리 알 수 없을 때 쓴다.
     */
    public String orchestrate(String goal) {
        Plan plan = strict.prompt()
                .user("다음 목표를 3~5개의 독립적인 하위 작업으로 쪼개라. 각 작업은 한 문장이다.\n---\n" + goal)
                .call()
                .entity(Plan.class);

        List<String> results = plan.subtasks().stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> chat.prompt().user(task).call().content(), aiExecutor))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();

        return chat.prompt()
                .system("여러 조각의 결과를 하나의 일관된 글로 합친다. 중복은 제거한다.")
                .user("[목표]\n" + goal + "\n\n[조각들]\n" + String.join("\n\n---\n\n", results))
                .call().content();
    }

    // ══════════════════════════════════════════════ ⑤ Chaining
    /**
     * 앞 단계의 출력이 다음 단계의 입력이 된다.
     * 각 단계가 작고 검증 가능해서 실패 지점이 눈에 보인다.
     */
    public List<String> extractThenTranslate(String document, String targetLang) {
        List<String> facts = strict.prompt()
                .user("다음 문서에서 핵심 사실만 불릿으로 추출하라. 해석·의견은 넣지 않는다.\n---\n" + document)
                .call()
                .entity(new ParameterizedTypeReference<List<String>>() {});

        return facts.stream()
                .map(fact -> strict.prompt()
                        .user(u -> u.text("다음 문장을 {lang}로 번역하라. 번역문만 출력한다.\n{text}")
                                .param("lang", targetLang)
                                .param("text", fact))
                        .call().content())
                .toList();
    }
}
