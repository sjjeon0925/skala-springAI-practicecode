# Day 2 실습 — 사내 문서 Q&A 만들기

- 인제스트부터 근거 있는 답까지
- 검색을 먼저 눈으로 본다
- 골든 세트로 측정하고 고친다
- 100분 · 2인 1조 권장

## 오늘 만들 것 — 문서 Q&A (Day 2 실습)

- 어제 만든 API 위에 근거를 붙인다
- 6단계 파이프라인을 직접 만든다
- 막히면 ch07-rag 예제를 연다

| 구분 | 무엇을 | 확인 방법 |
|---|---|---|
| 목표 | 사내 규정으로 답하고 출처를 붙이는 API | `POST /lab2/ask` |
| 파이프라인 | 읽기 → 분할 → 임베딩 → 저장 → 검색 → 생성 | 6단계를 코드로 |
| 응답 | 답변 · 출처 · 근거 사용 여부 | 구조화 출력(record) |
| 측정 | 골든 세트 10문항 | 통과율과 실패 유형 분류 |
| 실험 | 청크 크기 · top-k 조합 | 실험표 A~D를 채운다 |
| 산출물 | lab2 패키지 · golden.json | 완료 기준 8개 |
| 시간 | 100분 | 인제스트 25 · 답변 35 · 측정 25 · 정리 15 |

## 준비 — 문서 세 개와 골든 세트 (Day 2 실습)

- 문서는 짧고 서로 다른 세 개면 충분하다
- 골든 세트에 답이 없어야 하는 질문을 꼭 넣는다
- 정답 기준을 먼저 정하고 시작한다

```
src/main/resources/lab2-docs/
├─ return-policy.md      반품·교환 (단순변심 7일 · 배송비 고객 부담)
├─ shipping-policy.md    배송 정책 (제주·도서 추가비 · 평균 2~3일)
└─ membership.md         등급·포인트 (실버 1% · 골드 3%)
```

```json
# golden.json — 정답이 정해진 질문 10개 (아래는 4개만)
[
  {"q": "단순 변심 반품은 며칠 이내인가요?", "must": ["7일"],      "src": "return-policy"},
  {"q": "제주도는 배송비가 더 드나요?",       "must": ["추가"],     "src": "shipping-policy"},
  {"q": "골드 등급 적립률은?",                "must": ["3%"],       "src": "membership"},
  {"q": "우주 배송도 되나요?",                "must": ["확인되지"], "src": null}
]
# ↑ 마지막 문항이 핵심이다 — 문서에 없는 것을 지어내지 않는지 본다
# 표현을 바꾼 질문도 두어 개 넣는다 (검색이 표현에 얼마나 흔들리는지 본다)
#   "물건 돌려보내려면 며칠 안에 해야 해요?" → 같은 정답이어야 한다
```

## Step 1 — 인제스트: 메타데이터가 절반 (Day 2 실습)

- 읽기 → 분할 → 메타데이터 → 저장
- 출처·버전은 이 시점에만 넣을 수 있다
- 재인제스트로 중복이 쌓이지 않게 만든다

```java
@Service
public class Lab2IngestService {
    private final VectorStore vectorStore;

    public IngestResult ingest(Resource doc, String source, String version) {
        var reader = new TextReader(doc);                    // .md → Document
        reader.getCustomMetadata().put("source", source);    // 나중에 못 넣는다
        reader.getCustomMetadata().put("version", version);

        var splitter = TokenTextSplitter.builder()
                .withChunkSize(400)              // 토큰 기준 — 문서 성격에 맞춘다
                .withMinChunkSizeChars(200)
                .build();
        List<Document> chunks = splitter.apply(reader.get());

        vectorStore.delete(new FilterExpressionBuilder()      // 재색인 —
                .eq("source", source).build());               //   같은 출처를 지우고
        vectorStore.add(chunks);                               //   다시 넣는다

        return new IngestResult(source, chunks.size());
    }
}
// 확인: POST /lab2/ingest → [{"source":"return-policy","chunks":7}, ...]
```

## Step 2 — 검색을 먼저 눈으로 본다 (Day 2 실습)

- 답변보다 검색 API를 먼저 만든다
- 점수를 반드시 노출한다 — 감으로 판단하지 않는다
- 세 질문의 결과를 나란히 비교한다

```java
@GetMapping("/lab2/retrieve")            // 답변을 만들기 전에 이것부터 만든다
public List<Chunk> retrieve(@RequestParam String q,
                             @RequestParam(defaultValue = "4") int topK) {
    return vectorStore.similaritySearch(SearchRequest.builder()
                    .query(q).topK(topK)
                    .similarityThreshold(0.5)     // 낮은 점수는 근거가 아니다
                    .build())
            .stream()
            .map(d -> new Chunk(d.getMetadata().get("source").toString(),
                    d.getScore(),                     // 점수를 노출한다
                    snippet(d.getText(), 120)))
            .toList();
}
// 실습 — 아래 세 질문의 결과를 나란히 놓고 비교한다
//   ① "반품 기한"                   기대: return-policy 상위
//   ② "물건 돌려보내려면 며칠 안에?" 기대: 같은 문서 — 표현이 달라도 찾는가
//   ③ "우주 배송"                   기대: 점수가 전부 낮다 → 근거 없음
```

## Step 3 — 근거로 답하기 (Day 2 실습)

- 근거가 없으면 모델을 부르지 않는다
- 프롬프트에 거절 지시를 반드시 넣는다
- 응답은 문자열이 아니라 객체로 받는다

```java
public AnswerDto ask(String question) {
    var docs = retrieve(question, 4);
    if (docs.isEmpty()) {
        return AnswerDto.unknown();       // 근거가 없으면 모델을 부르지 않는다
    }
    return chatClient.prompt()
            .system("""
                아래 [근거]만 사용해 답한다. 근거에 없으면 "확인되지 않습니다"라고 답한다.
                추측하지 않는다. 답변 끝에 사용한 출처를 [출처: 파일명] 형식으로 남긴다.
                """)
            .user(u -> u.text("[근거]\n{context}\n\n[질문] {question}")
                    .param("context", format(docs))
                    .param("question", question))
            .call()
            .entity(AnswerDto.class);         // 구조화 출력 — 문자열 파싱 금지 (6장)
}

record AnswerDto(String answer, List<String> sources, boolean grounded) {
    static AnswerDto unknown() { return new AnswerDto("확인되지 않습니다.", List.of(), false); }
}
```

## Step 4 — 골든 세트로 측정 (Day 2 실습)

- 느낌으로 고치면 고쳤는지 알 수 없다
- 기준선을 코드에 박아 둔다
- 실패한 질문의 답을 반드시 읽는다

```java
@Test   // 모델을 부르므로 기본 테스트에서는 제외한다 (./gradlew test -Peval)
void 골든_세트_평가() throws Exception {
    var golden = mapper.readValue(resource("golden.json"),
            new TypeReference<List<Golden>>() {});
    int pass = 0;
    for (Golden g : golden) {
        AnswerDto a = service.ask(g.q());
        boolean hit  = g.must().stream().allMatch(k -> a.answer().contains(k));
        boolean cite = g.src() == null
                || a.sources().stream().anyMatch(s -> s.contains(g.src()));
        if (hit && cite) { pass++; }
        else { log.warn("실패: {}\n  답변: {}\n  출처: {}", g.q(), a.answer(), a.sources()); }
    }
    log.info("통과 {}/{}", pass, golden.size());
    assertThat(pass).isGreaterThanOrEqualTo(8);      // 기준선을 코드에 박아 둔다
}
// 실패를 두 종류로 나눠 적는다 — 고칠 곳이 완전히 다르다
//   ㉠ 근거를 못 찾았다 → 청킹 · 임베딩 · top-k · 질문 변환
//   ㉡ 찾고도 잘못 답했다 → 프롬프트 · 모델 · 근거 포맷
```

## Step 5 — 실험표를 채운다 (Day 2 실습)

- 정답은 문서마다 다르다 — 감이 아니라 기록으로 정한다
- 한 번에 하나만 바꾼다
- 채운 팀과 안 채운 팀의 차이가 실서비스 품질이 된다

| 조합 | 청크 | top-k | 통과율 / 관찰한 것 |
|---|---|---|---|
| A (기준) | 400토큰 · 겹침 0 | 4 | _ _ _ / 10 — 기준선 |
| B (작게) | 200토큰 | 4 | _ _ _ / 10 — 정확하지만 맥락이 부족한가? |
| C (크게) | 800토큰 | 4 | _ _ _ / 10 — 맥락은 넓지만 잡음이 늘었나? |
| D (넓게) | 400토큰 | 8 | _ _ _ / 10 — 근거도 비용도 함께 늘었나? |
| E (엄격) | 400토큰 · threshold 0.7 | 4 | _ _ _ / 10 — 거절이 늘었나? 정답도 거절했나? |
| F (겹침) | 400토큰 · 겹침 20% | 4 | _ _ _ / 10 — 잘린 문장 문제가 줄었나? |

## 자주 막히는 지점 — Day 2 실습

- 열에 아홉은 이 표 안에 있다
- 진단 순서는 언제나 같다
- 검색 → 프롬프트 → 그다음 모델

| 증상 | 원인 | 해결 |
|---|---|---|
| 검색 결과가 늘 비어 있다 | 임베딩 모델 미설정 · 미다운로드 | 키 확인 또는 임베딩 모델 설정 확인 (text-embedding-3-small) |
| 인제스트할수록 답이 나빠진다 | 중복 적재 | 재색인(삭제 후 삽입) 또는 id 고정 |
| 근거는 맞는데 답이 틀리다 | 프롬프트에 거절·인용 지시 없음 | 시스템 프롬프트 보강 |
| 항상 "확인되지 않습니다" | 임계값이 너무 높다 | threshold를 낮추고 점수 분포부터 확인 |
| 출처가 안 붙는다 | 메타데이터를 안 넣었다 | 인제스트 시점에 source 주입 |
| 컨텍스트 초과 오류 | top-k · 청크가 너무 크다 | k 축소 또는 근거를 요약해 투입 |
| 응답이 5초를 넘는다 | k 과다 · 임베딩 호출 반복 | 구간별 시간 측정부터 |

## 더 해 보기 — Day 2 확장 과제

- 하나 붙이고 골든 세트로 재고, 좋아지면 남긴다
- 여섯 개를 한꺼번에 붙이지 않는다
- 각 항목은 9장에서 다시 만난다

| 확장 과제 | 무엇을 배우나 | 힌트 · 참고 장 |
|---|---|---|
| 메타데이터 필터 | 권한·범위를 검색으로 강제 | team·tier 필터 — 프롬프트로 하지 않는다 |
| 하이브리드 검색 | 상품코드·고유명사에 강해진다 | 키워드 · 벡터 결합 (9장) |
| 재순위(rerank) | 후보 20 → 상위 4 재정렬 | 통과율 변화를 기록 (9장) |
| 질문 변환 | 구어체 질문에 강해진다 | HyDE · MultiQuery (9장) |
| 대화형 RAG | 후속 질문의 대명사 해석 | 메모리 결합 (12장) — 내일 주제 |
| pgvector 전환 | 재시작해도 남는 저장소 | `docker compose up -d` 후 스타터 교체 |

## 완료 기준 — Day 2, 그리고 내일

- 8개 중 6개 이상이면 오늘 목표 달성이다
- 4 · 8번이 오늘의 진짜 학습 지점이다
- 오늘 만든 것은 읽는 AI다

| # | 확인 항목 | 통과 기준 |
|---|---|---|
| 1 | 인제스트 | 문서 3종 적재 · 청크 수를 안다 |
| 2 | 검색 확인 | /lab2/retrieve로 근거를 눈으로 본다 |
| 3 | 출처 표기 | 답변에 출처가 함께 나온다 |
| 4 | 거절 | 문서에 없는 질문을 지어내지 않는다 |
| 5 | 구조화 응답 | record로 답변·출처·근거여부 반환 |
| 6 | 평가 | 골든 세트 8/10 이상 |
| 7 | 실험 | 실험표 A~D를 채웠다 |
| 8 | 재색인 | 두 번 인제스트해도 청크 수가 같다 |

## Day 2 되짚기 — 어제 만든 것 (Day 3 · 시작하며)

- 어제는 모델이 아는 것이 아니라 우리가 준 근거로 답하게 만들었다
- 프롬프트 → 구조화 출력 → RAG 순으로 답을 다듬어 왔다
- 오늘은 답하는 AI에서 일하는 AI로 넘어간다

| 어제 배운 것 | 한 줄 요약 | 오늘 어디에 쓰나 |
|---|---|---|
| PromptTemplate | 템플릿과 변수를 분리해 프롬프트를 재사용한다 | 도구 설명문도 같은 원리로 쓴다 |
| 구조화 출력 | entity()로 응답을 자바 객체로 받는다 | 도구의 입력과 출력도 객체로 오간다 |
| 임베딩 | 문장을 숫자 벡터로 바꿔 의미를 비교한다 | 검색 도구의 재료가 된다 |
| VectorStore | 문서를 넣어두고 비슷한 것을 꺼낸다 | 검색 자체를 하나의 도구로 감싼다 |
| QA Advisor | 찾은 근거를 프롬프트에 자동으로 붙여준다 | 오늘 Advisor의 정체를 제대로 밝힌다 |
| 골든 세트 측정 | 느낌 대신 통과율이라는 숫자로 판단한다 | 에이전트 품질도 같은 방식으로 잰다 |

## Day 2 체크리스트 — 여기까지 됐나 (Day 3 · 시작하며)

- 오늘 실습은 어제 만든 RAG 위에 도구를 얹는다 — 토대가 흔들리면 원인을 못 찾는다
- 아래 여섯 개 중 검색과 근거 답변, 이 둘은 반드시 살려 두자
- 막힌 조는 시작 전에 손을 들어 주면 조교가 붙는다

| 확인할 것 | 어떻게 확인하나 | 안 되면 이렇게 |
|---|---|---|
| 문서 인제스트 | 문서를 넣으면 VectorStore에 청크가 쌓인다 | 로더와 분할기 설정부터 다시 본다 |
| 검색 확인 | 검색 결과만 눈으로 보는 엔드포인트가 있다 | 답변보다 검색을 먼저 만든다 |
| 근거 답변 | 답과 함께 출처 문서가 같이 나온다 | Advisor가 실제로 붙었는지 확인 |
| 구조화 출력 | 응답이 자바 객체로 매핑된다 | 필드 이름과 설명을 다시 맞춘다 |
| 빈 결과 처리 | 검색 결과가 없을 때 답이 정해져 있다 | 빈 결과 분기를 먼저 넣는다 |
| 골든 세트 | 10문항 통과율을 숫자로 말할 수 있다 | 정답을 먼저 적고 채점한다 |

> **체크**: 검색이 죽은 상태로 오늘을 시작하면, 도구가 문제인지 검색이 문제인지 구분할 수 없다. 시작 전에 검색부터 살려 두자.

## 오늘의 지도 — Day 3 미리보기

- 오늘은 모델이 말하는 것을 넘어 실제로 일하게 만든다
- 오전은 도구와 통제, 오후는 운영과 종합 설계다
- 마지막 블록은 상담 에이전트를 완성하는 실습이다

| 시간 | 무엇을 배우나 | 끝나면 할 수 있는 것 |
|---|---|---|
| 10:00~11:00 · 10장 | Tool Calling · Agent · MCP | 모델이 우리 함수를 부르게 만든다 |
| 11:00~12:00 · 11장 | 감사 로깅 · 권한 제어 · 설계 원칙 | 도구 실행을 우리 통제 안에 둔다 |
| 13:10~14:00 · 12장 | Advisor 파이프라인 · 대화 메모리 | 대화를 기억하고 순서를 통제한다 |
| 14:00~15:00 · 12장 | 관찰 가능성 · 폴백 · 운영 | 장애를 미리 보고 대비한다 |
| 15:00~16:00 · 13장 | 종합 실습 — HelpDesk AI 설계 | 배운 것을 하나의 서비스로 엮는다 |
| 16:00~ 실습 | 상담 에이전트 완성하기 (110분) | 도구 · 권한 · 관찰까지 붙인 에이전트 |
