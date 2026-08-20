# 9. RAG 심화 – 고급 패턴

- HyDE 가상 답변 검색
- Agentic RAG
- Hybrid Search와 분할 전략
- RAG vs Fine-tuning

## RAG 품질 – 어디를 손보나 (심화 · RAG 고급 검색)

- RAG 실패는 대부분 검색 단계, 관련 근거를 못 찾은 것
- 끌어올리는 세 축 – 질의 개선 · 검색 방식 · 분할 전략
  - 질의 개선: HyDE · 검색 방식: Hybrid · 반복 검색: Agentic

> [참고] 모델을 바꾸기 전에 검색을 개선해 보라. 같은 모델, 같은 문서라도 무엇을 어떻게 찾아 넣느냐로 응답 품질이 크게 달라진다.

## HyDE – 가상 답변으로 검색 심화 (심화 · RAG 고급 검색)

- 짧은 질문은 실제 문서와 말투·형태가 다르다 → 검색이 빗나감
- 먼저 그럴듯한 가상 답변을 생성해, 그것으로 검색한다

질문 → 모델이 가상 답변 생성 → 그 답변을 임베딩해 검색. 답변끼리 닮은 문서를 더 잘 찾아 정확도가 오른다 (생성 1회가 추가된다).

- 그냥: 질문 → 임베딩 → 검색
- HyDE: 질문 → 가상 답변 생성 → 임베딩 → 검색

답변끼리 닮은 문서를 더 잘 찾는다 – 생성 1회가 추가된다.

## 모듈형 RAG – 네 구간으로 나눈다 (심화 · RAG 고급 검색)

- 질문 다듬기 → 검색 → 후처리 → 생성, 각 구간을 따로 갈아 끼운다
- RetrievalAugmentationAdvisor가 이 조립을 표준으로 제공
- 품질 문제는 어느 구간인지부터 지목해야 고칠 수 있다

| 구간 | 입력 | 컴포넌트 | 출력 |
|---|---|---|---|
| Pre | 질문 | QueryTransformer, QueryExpander | 다중 질의 |
| Retrieval | 다중 질의 | DocumentRetriever (필터 · topK) | 후보 문서 |
| Post | 후보 문서 | Re-rank, Join / 압축 | 근거 |
| Generate | 질문 + 근거 | ChatModel | 근거 있는 답 + 출처 |

Pre-Retrieval(질문 변환) · Retrieval(검색) · Post-Retrieval(재순위·압축) · Generation(생성). 각 구간이 독립 컴포넌트다.

## 질문 변환 – 검색이 잘 되는 형태로 (심화 · RAG 고급 검색)

- 사용자 질문은 짧고 애매하다 → 그대로 검색하면 잘 안 맞는다
- RewriteQueryTransformer(명료화) · TranslationQueryTransformer(언어 정렬)
- MultiQueryExpander로 여러 각도의 질의를 만들어 회수율을 올린다

```java
var advisor = RetrievalAugmentationAdvisor.builder()
    // ① Pre-Retrieval — 대화 맥락을 반영해 질문을 다시 쓴다
    .queryTransformers(RewriteQueryTransformer.builder()
        .chatClientBuilder(builder.build().mutate())
        .build())
    // ② 여러 변형 질의로 넓게 회수
    .queryExpander(MultiQueryExpander.builder()
        .chatClientBuilder(builder.build().mutate())
        .numberOfQueries(3)
        .build())
    // ③ Retrieval — 필터·임계값
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .similarityThreshold(0.6).topK(6)
        .build())
    .build();
String answer = chat.prompt().user(q).advisors(advisor).call().content();
```

> [주의] 질문 변환·확장은 모델 호출을 추가로 쓴다. 질의당 1~3회가 늘어난다 → 회수율이 실제로 올라가는지 측정한 뒤 켜라.

## Contextual Retrieval – 맥락 붙이기 (심화 · RAG 고급 검색)

- 청크를 잘라 놓으면 "이것", "해당 조항"이 무엇인지 알 수 없다
- 각 청크 앞에 문서 전체 맥락 한두 문장을 붙여 저장한다
- 인제스트 비용은 늘지만 회수율이 눈에 띄게 오른다

```java
// 인제스트 시점에 각 청크에 맥락 문장을 덧붙인다
String docSummary = chat.prompt()
    .user("이 문서가 무엇에 관한 것인지 2문장으로:\n" + fullText.substring(0, 4000))
    .call().content();
List<Document> contextualized = chunks.stream()
    .map(c -> {
        String prefix = "[문서: %s] %s\n\n".formatted(fileName, docSummary);
        // 검색 대상 텍스트에는 맥락을 포함하고,
        // 원문은 메타데이터에 남겨 답변 생성에 쓴다
        Map<String, Object> meta = new HashMap<>(c.getMetadata());
        meta.put("original", c.getText());
        return new Document(prefix + c.getText(), meta);
    })
    .toList();
vectorStore.add(contextualized);
```

## Agentic RAG – 검색을 도구로 (심화 · RAG 고급 검색)

- VectorStore를 Tool로 등록 → 에이전트가 검색 시점·질의를 판단
- 결과가 부족하면 질의를 바꿔 다시 검색(반복)

기본 RAG는 한 번 검색해 답한다. Agentic RAG는 검색을 도구로 삼아 부족하면 재검색·다른 질의를 시도한다 → 복잡한 질문에 강하나 스텝이 는다.

- 기본 RAG: 질문 → 검색 1회 → 답
- Agentic: 질문 → 검색 → (부족하면 질의 변경 → 재검색) → 답

복잡한 질문에 강하지만 스텝과 비용이 는다.

## Parent-Child – 작게 찾고 크게 준다 (심화 · RAG 고급 검색)

- 검색은 작은 청크가 정확하고, 답변은 큰 맥락이 낫다
- 작은 조각으로 찾은 뒤 그 조각이 속한 큰 단락을 모델에 넣는다
- 청크 크기 딜레마를 양쪽 다 취하는 방식으로 푼다

```java
// ① 인제스트 — 큰 단락을 쪼개고, 자식은 부모 ID를 들고 간다
for (Document parent : parentChunks) {          // 예: 1500 토큰
    parentStore.put(parent.getId(), parent.getText());
    for (Document child : split(parent, 300)) { // 예: 300 토큰
        Map<String, Object> meta = new HashMap<>(child.getMetadata());
        meta.put("parentId", parent.getId());
        vectorStore.add(List.of(new Document(child.getText(), meta)));
    }
}
// ② 검색 — 자식으로 찾고, 부모를 꺼내 중복 제거 후 투입
List<Document> hits = vectorStore.similaritySearch(
    SearchRequest.builder().query(q).topK(8).build());
String context = hits.stream()
    .map(d -> (String) d.getMetadata().get("parentId"))
    .distinct()                              // 같은 부모는 한 번만
    .map(parentStore::get)
    .collect(Collectors.joining("\n---\n"));
```

## 기본 코드 틀 – 검색 Tool (심화 · RAG 고급 검색)

- VectorStore 검색을 `@Tool` 메서드로 감싸 에이전트에 준다

```java
@Component
class SearchTools {
    private final VectorStore vectorStore;

    @Tool(description = "사내 문서에서 관련 조각을 검색한다")
    List<String> searchDocs(
            @ToolParam(description="검색어") String query) {
        return vectorStore.similaritySearch(query)
                .stream().map(Document::getText).toList();
    }
}
```

## 재순위(Re-rank) – 다시 정렬한다 (심화 · RAG 고급 검색)

- 벡터 유사도 상위 = 정답 순서는 아니다 → 넓게 뽑아 다시 정렬
- topK 20으로 회수 → 재순위 후 상위 4건만 모델에 넣는다
- 근거가 짧아지니 정확도는 오르고 토큰은 준다

```java
public List<Document> rerank(String question, List<Document> candidates) {
    String numbered = IntStream.range(0, candidates.size())
            .mapToObj(i -> "[" + i + "] " + candidates.get(i).getText())
            .collect(Collectors.joining("\n---\n"));
    Ranking r = chat.prompt()
            .system("질문에 답하는 데 실제로 쓸모 있는 문단만 골라 순서대로 번호를 나열하라.")
            .user("질문: " + question + "\n\n후보:\n" + numbered)
            .options(ChatOptions.builder().temperature(0.0).build())
            .call().entity(Ranking.class);
    return r.indexes().stream().limit(4)
            .map(candidates::get).toList();          // 상위 4건만 근거로
}
record Ranking(List<Integer> indexes) {}
```

## Hybrid Search – 키워드 + 의미 (심화 · RAG 고급 검색)

- 벡터 검색만으론 정확한 용어·품번·코드를 놓칠 수 있다
- 키워드 검색과 결합해 순위를 재조정 → 둘의 장점을 합친다

키워드 검색(정확한 용어)과 벡터 검색(의미)을 합쳐 순위를 재조정한다. 의미도 잡고 정확한 용어도 놓치지 않는다.

- 키워드 검색: 정확한 용어 · 품번 · 코드를 잡는다
- 벡터 검색: 표현이 달라도 의미로 찾는다
- 둘을 합쳐 순위를 재조정한다 → 의미도 잡고 용어도 놓치지 않는다

## 분할 전략 – 무엇을 고를까 (심화 · RAG 설계 선택)

- 고정 길이 · 문장/문단 · 의미(구조) 기반 → 문서 성격에 맞게
- 공통 요령: 조각끼리 약간 겹치게(overlap) 둔다

고정 길이는 쉽지만 문장을 자르고, 문단·의미 기반은 맥락을 지킨다. 규정·매뉴얼처럼 구조가 뚜렷하면 조항 단위(의미 기반)가 가장 정확하다.

| 방식 | 특징 |
|---|---|
| 고정 길이 | 쉽지만 문장을 자른다 |
| 문장 · 문단 | 맥락을 지킨다 – 무난한 기본값 |
| 의미(구조) 기반 | 조항 단위 – 규정·매뉴얼에 가장 정확 |

공통 요령: 조각끼리 약간 겹치게(overlap) 둔다.

## GraphRAG – 관계를 따라가는 검색 (심화 · RAG 설계 선택)

- "A와 B의 관계는?" 같은 질문은 한 청크에 답이 없다
- 개체와 관계를 그래프로 뽑아 두고 연결을 따라간다
- 구축 비용이 크다 → 관계형 질문이 실제로 많을 때만

| 질문 유형 | 예 | 벡터 RAG | GraphRAG |
|---|---|---|---|
| 단일 사실 | "반품 기간은?" | ✅ 잘한다 | 과함 |
| 요약 | "이 규정의 요지는?" | ✅ 잘한다 | 과함 |
| 관계 추적 | "이 조항은 어느 규정에서 파생?" | ❌ 약함 | ✅ 강점 |
| 다중 홉 | "A팀 담당자의 상급자는?" | ❌ 약함 | ✅ 강점 |
| 전체 조망 | "전 규정에서 반복되는 주제는?" | ❌ 약함 | ✅ 강점 |

> [주의] 대부분의 사내 Q&A는 단일 사실 질문이다. GraphRAG를 먼저 검토하기보다 벡터 RAG로 시작해 못 푸는 질문이 쌓일 때 도입을 논하는 편이 낫다.

## RAG vs Fine-tuning – 선택 기준 (심화 · RAG 설계 선택)

- 지식이 필요하면 RAG, 행동·형식을 바꾸려면 Fine-tuning
- 둘은 경쟁이 아니라 목적이 다르다 → 함께 쓰기도 한다

RAG는 최신·내부 지식을 근거로(문서만 갈면 갱신·출처 표시), Fine-tuning은 말투·형식·행동을 굳힐 때. 실무 순서는 프롬프트 → RAG → FT.

- RAG: 최신·내부 지식을 근거로 · 문서만 갈면 갱신
- Fine-tuning: 말투·형식·행동을 굳힐 때

실무 순서는 프롬프트 → RAG → Fine-tuning.

## 정리 – 검색을 지배하라 (심화 · RAG 설계 선택)

- 질의 개선(HyDE) · 반복 검색(Agentic) · 방식 결합(Hybrid)
- 분할은 문서 성격에 맞게, 겹침을 둔다
- 지식은 RAG, 행동은 Fine-tuning → 대개 RAG로 충분

> [정리] RAG 품질은 결국 검색을 얼마나 잘하느냐다. 모델을 바꾸기 전에 질의·방식·분할을 손봐라. 다음은 Tool을 안전하고 관리 가능하게 만드는 심화다.

## RAG 평가 – 무엇을 재나 (심화 · RAG 설계 선택)

- RAG는 검색과 생성 두 단계라 지표도 나눠서 봐야 한다
- "답이 좋다/나쁘다"로 뭉뚱그리면 어디를 고칠지 알 수 없다
- 네 지표면 충분하다 → 완벽한 평가보다 꾸준한 측정

| 단계 | 지표 | 무엇을 묻나 | 낮으면 |
|---|---|---|---|
| 검색 | Recall@k | 정답 문서가 상위 k 안에 있는가 | 청킹·임베딩·질문 변환 |
| 검색 | Precision@k | 가져온 것 중 쓸모 있는 비율 | 임계값 조정 · 재순위 |
| 생성 | Faithfulness | 근거 안의 내용만으로 답했는가 | 시스템 프롬프트 강화 |
| 생성 | Answer Relevancy | 질문에 실제로 답했는가 | 프롬프트 · 모델 상향 |

```java
// Spring AI 내장 평가기 — 근거 충실도를 모델로 채점한다
var evaluator = new RelevancyEvaluator(chatClientBuilder);
var request = new EvaluationRequest(question, retrievedDocs, answer);
EvaluationResponse result = evaluator.evaluate(request);
assertThat(result.isPass()).isTrue();
```

## 미니 실습 – 실패 하나 고치기 (25분) (심화 · RAG 설계 선택)

- 기법을 다 붙이지 않는다 → 하나씩 붙이고 잰다
- 고칠 실패 사례를 먼저 고른다
- ch08/ragadv를 참고한다

| 단계 | 무엇을 한다 | 확인 기준 |
|---|---|---|
| ① | 8미니 실습에서 틀린 질문 하나를 고른다 | 재현되는 실패 하나면 충분하다 |
| ② | 검색 결과부터 확인 → 근거가 있었나? | ㉠못 찾음 / ㉡찾고 잘못 답함 분류 |
| ③ | ㉠이면 질문 변환(HyDE·MultiQuery) 적용 | 근거가 검색되기 시작하는가 |
| ④ | 여전히 순위가 낮으면 재순위 적용 | 상위 4개 안에 들어오는가 |
| ⑤ | 고유명사 문제면 하이브리드 검색 | 키워드 매칭이 살아나는가 |
| ⑥ | 적용 전후를 같은 질문 5개로 비교 | 좋아졌으면 남기고, 아니면 버린다 |

> [주의] 한 번에 하나만 붙인다. 세 기법을 동시에 붙이면 무엇이 효과였는지 알 수 없고, 지연과 비용만 확실히 늘어난다 → 되돌릴 근거도 남지 않는다.

## 실습 코드 – 못 찾던 질문 고치기 (HyDE) (심화 · RAG 설계 선택)

- 구어체 질문이 왜 안 찾히는지 눈으로 본다
- 가상의 답을 만들어 그 문장으로 검색한다
- 점수가 올라가는 것을 확인한다

```java
// ① 실패하는 질문 하나를 고른다 (문서 말과 사용자 말이 다른 경우)
String q = "물건 돌려보내려면 며칠 안에 해야 해요?";
var 그냥 = store.similaritySearch(SearchRequest.builder().query(q).topK(3).build());
그냥.forEach(d -> System.out.printf("그냥검색 %.2f  %s%n", d.getScore(), 앞부분(d)));

// ② HyDE — 가상의 답을 먼저 만들고, 그 문장으로 검색한다
String 가상답 = chat.prompt()
    .user("다음 질문에 대한 그럴듯한 답을 2문장으로 써라(사실 여부는 상관없다): " + q)
    .call().content();
// 가상답 예: "반품은 수령 후 일정 기간 안에 가능합니다. 보통 7일 이내입니다."
var 개선 = store.similaritySearch(SearchRequest.builder()
    .query(가상답).topK(3).build());                    // ← 질문 대신 가상답으로 검색
개선.forEach(d -> System.out.printf("HyDE    %.2f  %s%n", d.getScore(), 앞부분(d)));

// 결과 예 —
//   그냥검색 0.41  배송 정책 안내...        ← 엉뚱한 문서가 1등
//   HyDE    0.68  반품은 수령 후 7일...     ← 정답 문서가 1등으로 올라온다

// ③ 같은 질문 5개로 전후를 비교하고, 좋아지지 않으면 되돌린다(붙인 게 아까워도)
```

## 실행·테스트 – HyDE 전후 비교 (심화 · RAG 설계 선택)

- 8장 문서가 인제스트돼 있어야 시작할 수 있다
- 구어체 질문 하나로 전후를 같은 화면에서 비교한다
- 테스트는 회수된 문서가 바뀌었는지만 본다

```bash
# → 실행: SpringAI_실습/09_HyDE비교 폴더를 VS Code로 열고 F5 (또는 ./gradlew bootRun)

# 1) 준비 — 8미니 실습의 문서가 이미 인제스트돼 있어야 한다
curl -X POST localhost:8080/lab8/ingest

# 2) 전후를 같은 화면에서 비교한다
curl 'localhost:8080/lab9/compare?q=물건 돌려보내려면 며칠 안에 해야 해요?'

# 3) 기대 결과 — 점수와 1등 문서가 바뀐다
그냥검색 0.41  배송 정책 안내...        ← 엉뚱한 문서가 1등
HyDE    0.68  반품은 수령 후 7일...     ← 정답 문서가 1등

# 4) 질문 5개로 전후를 재고 표에 적는다
for q in "반품 기한" "물건 돌려보내려면" "환불 언제까지" "교환 되나요" "제주 배송비"; do
  curl -s --get --data-urlencode "q=$q" localhost:8080/lab9/compare | head -2
done
# 좋아진 질문 / 나빠진 질문을 세어 본다 — 5개 중 3개 이상 좋아지면 채택

# 5) 테스트 — 기법이 아니라 '개선 여부'를 검증한다
@Test void HyDE_가_구어체_질문을_개선한다() {
    double before = 최고점수(그냥검색("물건 돌려보내려면 며칠 안에 해야 해요?"));
    double after  = 최고점수(HyDE검색("물건 돌려보내려면 며칠 안에 해야 해요?"));
    assertThat(after).isGreaterThan(before);
}
# 안 되면 — 차이 없음: 질문이 이미 문서 말투와 같다(그럴 땐 안 쓰는 게 맞다)
```

## 핵심 요약 – RAG 심화 (심화 · RAG 설계 선택)

- 이 장의 결론 → 넓게 찾고 좁게 넣는다
- 검색이 못 찾은 것과 모델이 못 쓴 것은 고치는 곳이 다르다

| 기법 | 무엇을 해결하나 | 비용·주의 |
|---|---|---|
| HyDE | 질문과 문서의 문체 차이 | 모델 호출 1회 추가 |
| 질문 변환 | 짧고 애매한 질문 | 대화 맥락을 반영해 다시 쓴다 |
| 다중 질의 확장 | 회수율 부족 | 질의당 1~3회 추가 호출 |
| 재순위 | 유사도 상위 = 정답 순서 아님 | 효과 대비 노력이 가장 좋다 |
| Hybrid Search | 제품 코드 등 정확 일치 | 검색 엔진 기능을 쓰는 편이 낫다 |
| 모듈형 RAG | 구간별로 갈아 끼우기 | 어느 구간 문제인지부터 지목 |
| RAG vs 파인튜닝 | 지식은 RAG, 문체는 튜닝 | 먼저 RAG로 시작한다 |

> [체크] topK 20 회수 → 재순위 → 상위 4건만 투입. 정확도는 오르고 토큰은 준다.
