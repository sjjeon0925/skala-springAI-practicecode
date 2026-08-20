# 7. LLM 활용 심화

- 단위 테스트(MockModel)
- 호출 최적화 파이프라인
- Router 패턴
- CoT·구조화 출력 심화

## 왜 AI 코드도 테스트하나

*심화 · 테스트와 최적화*

- AI 응답은 매번 달라 그대로는 테스트가 어렵다
- 해법: 가짜(Mock) ChatModel로 응답을 고정하고, 우리 로직만 검증
  - 프롬프트를 제대로 조립하는가 · 응답을 옳게 후처리·분기하는가

가짜 ChatModel이 정해진 응답을 반환 → 우리 서비스의 로직만 검증한다. 공급자 호출·비용·비결정성이 없어 빠르고 CI에 넣을 수 있다.

가짜 ChatModel → 정해진 응답 → 우리 서비스 로직 검증

공급자 호출·비용·비결정성이 없다 → CI에 넣을 수 있다

## 모델 선택 — 무엇을 기준으로

*심화 · 테스트와 최적화*

- "가장 좋은 모델"이 아니라 "이 작업에 충분한 모델"을 고른다
- 분류·추출에 최상위 모델을 쓰는 것은 대부분 낭비다
- 작업별로 나눠 쓰면 비용이 몇 배 차이 난다

| 작업 | 필요한 능력 | 권장 | 이유 |
|---|---|---|---|
| 분류 · 라벨링 | 형식 준수 | 소형 · 온도 0 | 정답이 정해져 있다 |
| 정보 추출 | 형식 및 정확도 | 소형~중형 | 구조화 출력이 대신 잡아 준다 |
| 요약 | 문장력 | 중형 | 품질 차이가 눈에 보인다 |
| 상담 응답 | 문장력 및 맥락 | 중형~대형 | 사용자가 직접 읽는다 |
| 복잡한 추론 | 다단계 논리 | 대형 · 추론 모델 | 여기서만 값을 한다 |
| 코드 생성·리뷰 | 정확도 | 대형 | 틀리면 되돌리는 비용이 크다 |

## 기본 코드 틀 — Mock 테스트

*심화 · 테스트와 최적화*

```java
@Test
void 요약_서비스가_모델_응답을_반환한다() {
    // given: 가짜 ChatModel이 정해진 답을 준다
    ChatModel model = mock(ChatModel.class);
    given(model.call(any(Prompt.class)))
        .willReturn(chatResponseOf("요약본")); // 헬퍼(예시)
    var service = new SummaryService(ChatClient.create(model));

    // when & then: 우리 로직 검증
    assertThat(service.summarize("긴 글")).isEqualTo("요약본");
}
```

## 결정론적으로 테스트하기

*심화 · 테스트와 최적화*

- AI 응답은 매번 달라진다 → 그대로 두면 테스트가 흔들린다
- 흔들리지 않게 만드는 방법은 세 층 — 모킹 · 계약 검증 · 골든셋
- 답 내용을 단정하는 테스트는 만들지 않는다

| 층 | 무엇을 검증 | 모델 호출 | 언제 돌리나 |
|---|---|---|---|
| 모킹 | 응답 처리 로직 · 예외 · 변환 | 없음 | 매 커밋(CI 기본) |
| 계약 검증 | 형식·필수 필드·범위 | 있음(소량) | 일 1회 또는 배포 전 |
| 골든셋 평가 | 품질 회귀(통과율) | 있음(30문항) | 프롬프트·모델 변경 시 |

```java
// ❌ 이렇게 쓰면 매번 깨진다
assertThat(answer).isEqualTo("반품은 7일 이내에 가능합니다.");

// ✅ 형식과 계약을 검증한다
assertThat(ticket.category()).isIn("BILLING", "DELIVERY", "REFUND", "ETC");
assertThat(answer).contains("7일");            // 핵심 사실만
assertThat(response.sources()).isNotEmpty();   // 근거가 붙었는가
```

## 호출 최적화 파이프라인

*심화 · 테스트와 최적화*

- 가장 싼 호출은 부르지 않는 호출 — 캐시로 반복 제거
- 쉬운 건 작은 모델로 라우팅, 컨텍스트는 필요한 것만

요청 → 캐시 확인 → 난이도별 라우팅 → 컨텍스트 축소 → 모델 호출. 부르기 전에 걸러 내고, 싼 경로부터 태워 비용·지연을 동시에 낮춘다.

요청 → 캐시 확인 → 난이도별 라우팅 → 컨텍스트 축소 → 모델 호출

부르기 전에 걸러 내고, 싼 경로부터 태운다

## 기본 코드 틀 — 응답 캐시

*심화 · 테스트와 최적화*

- 반복되는 질문은 캐시로 — 모델 호출 자체를 없앤다

```java
@Service
class CachedAiService {
    private final ChatClient chat;

    @Cacheable("ai-answers") // 같은 질문이면 캐시 반환
    public String ask(String question) {
        return chat.prompt()
            .user(question)
            .call()
            .content();
    }
}
```

[주의] 캐시 키는 정규화한다(공백·대소문자). 개인화·실시간 데이터가 섞인 질문은 캐시하면 안 된다 — 불변에 가까운 질문에만 적용한다.

## 프롬프트 캐싱 — 반복되는 앞부분

*심화 · 테스트와 최적화*

- 공급자의 프롬프트 캐싱은 반복되는 앞부분을 싸게 처리한다
- 변하지 않는 것을 앞에 두는 것이 전부다 — 순서가 곧 최적화

| 순서 | 내용 | 매 요청 동일? | 캐시 |
|---|---|---|---|
| ① | 시스템 프롬프트(역할·규칙) | 동일 | ✅ 대상 |
| ② | 공통 지침 · 용어집 | 동일 | ✅ 대상 |
| ③ | Few-shot 예시 | 동일 | ✅ 대상 |
| ④ | 검색된 근거 | 질문마다 다름 | — |
| ⑤ | 대화 이력 | 턴마다 다름 | — |
| ⑥ | 이번 질문 | 매번 다름 | — |

```java
// ❌ 흔한 실수 — 매번 바뀌는 값을 앞부분에 넣는다
.defaultSystem("오늘은 " + LocalDate.now() + "입니다. 너는 상담원이다...")
//    → 날짜가 바뀌면 ①이 달라져 캐시가 통째로 무효

// ✅ 고정된 것만 앞에, 가변값은 뒤(사용자 메시지)로
.defaultSystem(systemPrompt)                        // 항상 동일
.user(u -> u.text("[오늘 {today}] {question}")
    .param("today", LocalDate.now())
    .param("question", question))
```

[주의] 시스템 프롬프트에 현재 시각을 넣지 마라 — 한 글자만 달라져도 캐시가 전부 무효다.

## Router 패턴 — 유형별 경로

*심화 · Router와 구조화*

- 먼저 질문 유형을 분류하고, 유형에 맞는 처리로 보낸다
  - 단순 FAQ·문서 질문·복잡 추론을 각기 다른 경로로

분류기가 유형을 판단 → FAQ는 작은 모델·캐시, 문서 질문은 RAG, 복잡 추론은 큰 모델·에이전트로. 비용·품질·지연을 동시에 잡는다.

질문 → 분류기 → 유형 판단

- 단순 FAQ → 작은 모델 · 캐시
- 문서 질문 → RAG (근거를 붙여 답한다)
- 복잡 추론 → 큰 모델 · 에이전트

## 기본 코드 틀 — Router

*심화 · Router와 구조화*

```java
public String route(String q) {
    // 1) 유형 분류 (작은 모델·규칙으로 가볍게)
    String type = classifier.prompt()
        .user("유형을 FAQ/DOC/COMPLEX 중 하나로: " + q)
        .call().content().trim();

    // 2) 유형별 경로로 위임
    return switch (type) {
        case "FAQ" -> faqClient.prompt().user(q).call().content();
        case "DOC" -> ragClient.prompt().user(q).call().content();
        default -> agentClient.prompt().user(q).call().content();
    };
}
```

## 워크플로 패턴 — 쪼개서 조립한다

*심화 · Router와 구조화*

- 복잡한 일을 한 번의 거대한 프롬프트로 밀면 정확도가 떨어진다
- 이어 붙이기·나눠 처리·유형별 분기·평가 루프 — 조합이 정답
- 각 단계는 작고 검증 가능한 호출 — 실패 지점이 눈에 보인다

| 패턴 | 흐름 |
|---|---|
| Chaining | 입력 → 단계1 → 단계2 → 단계3 → 결과 |
| Parallel | 입력 → 작업 A · B · C (동시) → 집계 → 결과 |
| Routing | 입력 → 분류기 → 전용 경로 선택 → 결과 |
| Orchestr. | 입력 → Orchestrator → Worker × N → Synthesizer → 결과 |
| Eval-Opt | 초안 → Evaluator → 피드백 → 재작성 → 합격 시 종료 |

다섯 가지 기본형. 실무 파이프라인은 대개 이 조합이다 — 분류로 갈라(Routing) 병렬로 처리하고(Parallel) 마지막에 평가(Eval)한다.

## 병렬 처리 — 나눠서 동시에

*심화 · Router와 구조화*

- 서로 의존하지 않는 작업은 동시에 호출해 지연을 줄인다
- 문서 N건 요약, 여러 관점 평가, 다국어 번역이 대표적
- 동시성 상한을 두지 않으면 레이트 리밋에 걸린다 — 반드시 제한

```java
@Service
class ParallelSummaryService {
    private final ChatClient chat;
    private final Executor aiExecutor;      // 상한이 걸린 전용 풀

    public List<String> summarizeAll(List<String> docs) {
        List<CompletableFuture<String>> futures = docs.stream()
            .map(doc -> CompletableFuture.supplyAsync(
                () -> chat.prompt().user("3문장 요약: " + doc)
                    .call().content(),
                aiExecutor))                       // 풀 크기 = 동시 상한
            .toList();

        return futures.stream()
            .map(f -> f.completeOnTimeout("(요약 실패)", 30, TimeUnit.SECONDS))
            .map(CompletableFuture::join).toList();
    }
}
```

[주의] 병렬은 지연을 줄이지 비용을 줄이지 않는다. 호출 수는 그대로다. 전용 스레드 풀로 동시 호출 수를 묶어 두지 않으면 429(레이트 리밋)를 만난다.

## CoT 심화 — 사고는 하되 감추기

*심화 · Router와 구조화*

- 복잡한 문제는 단계적 사고로 정확도를 올린다(Chain-of-Thought)
- 단, 사용자에겐 최종 결과만 보여 사고 과정을 감출 수 있다
  - 형식을 지정해 근거는 내부적으로, 결론만 출력

[참고] 사고 과정을 다 노출하면 길고 산만하다. 구조화 출력으로 {추론, 결론}을 받아 결론만 사용자에게 보이고, 추론은 로깅·디버깅용으로 남기는 방식이 실전적이다.

## 구조화 출력 심화 — enum·검증

*심화 · Router와 구조화*

- enum으로 값의 범위를 고정 — 예상 밖 값 차단
- 받은 객체를 평범한 자바 검증으로 한 번 더 확인

```java
enum Priority { HIGH, MEDIUM, LOW }
record Ticket(String title, Priority priority,
              List<String> tags) {}

Ticket t = chat.prompt().user(text)
    .call().entity(Ticket.class);
// t.priority()는 세 값 중 하나로 보장 (예시)
if (t.tags().isEmpty()) { /* 재요청·기본값 */ }
```

[주의] 구조화 출력도 완벽하지 않다. 타입은 맞아도 내용이 틀릴 수 있으니, 중요한 값은 받은 뒤 코드로 한 번 더 검증한다 — AI를 신뢰의 끝점으로 두지 않는다.

## Evaluator-Optimizer 패턴

*심화 · Router와 구조화*

- 생성 → 평가 → 피드백을 반영해 재생성을 정해진 횟수만 반복
- 번역 품질·보고서 문체처럼 기준이 명확한 작업에서 효과가 크다
- 호출이 2~3배로 는다 — 품질이 비용보다 중요할 때만

```java
public String writeWithReview(String topic, int maxRounds) {
    String draft = chat.prompt().user("다음 주제로 초안: " + topic)
        .call().content();

    for (int round = 0; round < maxRounds; round++) {
        Review review = chat.prompt()
            .system("너는 엄격한 편집자다. 통과 여부와 개선점을 판정하라.")
            .user("초안:\n" + draft).call().entity(Review.class);

        if (review.passed()) return draft;             // 합격 — 종료

        draft = chat.prompt()
            .user("아래 지적을 반영해 다시 써라.\n지적: " + review.feedback()
                + "\n초안:\n" + draft)
            .call().content();
    }
    return draft;                                      // 상한 도달
}
record Review(boolean passed, String feedback) {}
```

## 정리 — 실무 견고함

*심화 · Router와 구조화*

- 테스트: 모델을 Mock으로 두고 로직을 검증 — CI에 넣는다
- 최적화: 캐시·라우팅·컨텍스트 축소로 비용·지연을 낮춘다
- Router·구조화 심화로 경로와 출력을 견고하게 — enum·검증 필수

[정리] 심화의 공통 주제는 견고함이다 — 비결정적 AI를 예측 가능한 서비스로 감싼다. 다음은 RAG를 한 단계 더 끌어올리는 고급 패턴이다.

## 배치 처리 — 대량을 싸게

*심화 · Router와 구조화*

- 지금 당장 답이 필요 없는 일은 실시간으로 처리할 이유가 없다
- 문서 분류·태깅·요약 같은 일괄 작업이 대상
- 한 번에 여러 건을 묶는 것만으로도 호출 수가 크게 준다

```java
// ① 건별 호출 — 100건이면 100번
for (Doc d : docs) classify(d);                    // 느리고 비싸다

// ② 묶어서 호출 — 10건씩이면 10번
record Item(int index, String text) {}
record Labeled(int index, String category) {}

public List<Labeled> classifyBatch(List<Item> batch) {
    return strict.prompt()
        .user("각 항목을 분류하라. index 를 그대로 유지한다.\n"
            + toJson(batch))
        .call()
        .entity(new ParameterizedTypeReference<List<Labeled>>() {});
}

// ③ 실패 시 — 묶음 전체를 버리지 말고 건별로 되돌린다
//    묶음이 깨지면 그 묶음만 개별 호출로 재처리
```

## 미니 실습 — 라우팅과 교정 (25분)

*심화 · Router와 구조화*

- 한 번의 호출로 안 되는 일을 쪼갠다
- 패턴 두 개를 직접 조립한다
- ch06/advanced를 참고한다

| 단계 | 무엇을 한다 | 확인 기준 |
|---|---|---|
| ① | 질문 유형 분류기(작은 모델·온도 0) | 3~4개 유형으로 안정 분류 |
| ② | 유형별로 다른 프롬프트·모델로 라우팅 | 간단한 질문은 싼 모델로 |
| ③ | ①②의 지연·비용을 단일 호출과 비교 | 총비용이 줄어드는지 확인 |
| ④ | 평가자 프롬프트 추가(0~5점 채점) | 낮은 점수면 교정 1회 |
| ⑤ | 교정 루프에 상한 1회를 건다 | 무한 개선을 막는다 |
| ⑥ | 응답 캐시 적용 전후 비교 | 같은 질문 두 번째는 즉시 |

## 실습 코드 — 스타일 라우터와 자가 채점

*심화 · Router와 구조화*

- 질문마다 짧게 · 자세히 · 농담 중 하나로 간다
- 답을 만든 뒤 스스로 점수를 매긴다
- 낮으면 딱 한 번만 고친다

```java
enum 스타일 { 짧게, 자세히, 농담 }
record 점수(int value, String reason) {}

@GetMapping("/lab7/ask")
String ask(@RequestParam String q) {
    스타일 route = 분류기.prompt()                       // ① 먼저 고른다(싼 모델·온도 0)
        .system("질문에 어울리는 답변 스타일을 하나 고른다.")
        .user(q).call().entity(스타일.class);

    String 답 = switch (route) {                          // ② 경로별로 다르게 답한다
        case 짧게 -> 작은모델.prompt().system("한 문장으로만 답한다.").user(q)
            .call().content();
        case 자세히 -> 큰모델.prompt().system("단계별로 자세히 설명한다.").user(q)
            .call().content();
        case 농담 -> 큰모델.prompt().system("농담을 섞되 사실은 정확히.").user(q)
            .call().content();
    };

    점수 s = 평가자.prompt()                              // ③ 스스로 채점한다
        .user("질문:%s%n답변:%s%n0~5점과 이유를 매겨라".formatted(q, 답))
        .call().entity(점수.class);

    if (s.value() < 3)                                    // ④ 낮을 때만, 한 번만 고친다
        답 = 큰모델.prompt().user("아래 답을 더 낫게 고쳐라: " + 답).call().content();

    log.info("route={} score={} 모델호출={}회", route, s.value(), s.value()<3 ? 3 : 2);
    return 답;   // ⑤ 이 로그가 '쪼갠 값어치'를 판단하는 근거가 된다
}
```

[주의] ④번이 while이 아니라 if 인 것을 보라. 모델은 계속 고치라고 하면 끝없이 고친다 — 교정 루프에는 반드시 상한이 있어야 한다.

## 실행·테스트 — 라우터와 교정

*심화 · Router와 구조화*

- 성격이 다른 질문 세 개를 던져 경로가 갈리는지 본다
- 어느 갈래로 갔는지 로그에 남겨 두면 나중이 편하다
- 테스트는 분류 결과만 확인한다 — 답 내용은 보지 않는다

```bash
# 1) 파일 위치
src/main/java/com/skala/lab7/RouterLab.java
#    → 실행: SpringAI_실습/07_라우터와교정 폴더를 VS Code 로 열고 F5 (또는 ./gradlew bootRun)

# 2) 세 가지 성격의 질문으로 호출한다
curl 'localhost:8080/lab7/ask?q=JVM 이 뭐야'                  # → 짧게
curl 'localhost:8080/lab7/ask?q=GC 동작을 단계별로 설명해줘'   # → 자세히
curl 'localhost:8080/lab7/ask?q=개발자 유머 하나'              # → 농담

# 3) 로그에서 경로와 호출 수를 확인한다
route=짧게 score=4 모델호출=2회
route=자세히 score=2 모델호출=3회   # 낮은 점수 → 한 번 교정

# 4) 측정 — 쪼갠 값어치가 있는지 본다
#   단일 호출 평균 1.9s / 1,200토큰
vs   라우팅 평균 2.6s / 1,900토큰
#   → 품질이 그만큼 좋아졌는지 골든 세트로 확인하고 판단한다

# 5) 테스트 — 분류기만 따로 검증한다(가장 값싸고 안정적이다)
@Test void 질문_유형을_고른다() {
    assertThat(lab.route("JVM 이 뭐야")).isEqualTo(스타일.짧게);
    assertThat(lab.route("개발자 유머 하나")).isEqualTo(스타일.농담);
}

# 안 되면 — 늘 같은 경로: 분류 프롬프트에 예시를 넣는다
#           교정이 반복: if 인지 while 인지 확인(상한 필수)
```

## 핵심 요약 — LLM 활용 심화

*심화 · Router와 구조화*

- 이 장의 결론 — 정확도가 안 나오면 프롬프트를 늘리지 말고 호출을 쪼개라
- 쪼갠 단계는 각각 테스트할 수 있고, 비싼 모델은 필요한 곳에만 쓴다

| 패턴 | 언제 쓰나 | 주의할 점 |
|---|---|---|
| Routing | 유형별로 처리 방식이 다를 때 | 분류는 값싼 모델·온도 0 |
| Parallel | 서로 독립적인 작업이 여러 건 | 지연만 줄고 비용은 그대로·상한 필수 |
| Chaining | 앞 결과가 뒤의 입력이 될 때 | 실패 지점이 눈에 보이는 것이 장점 |
| Evaluator-Optimizer | 품질이 비용보다 중요할 때 | 평가자는 다른 관점·반복 상한 |
| Orchestrator | 작업 개수를 미리 모를 때 | 쪼갠 결과를 합치는 단계까지 설계 |
| 캐시·테스트 | 같은 질문 반복·회귀 방지 | 개인화·실시간 답변은 캐시 금지 |

[체크] 프롬프트가 계속 길어지고 있다면 쪼갤 때를 지났다는 신호다.
