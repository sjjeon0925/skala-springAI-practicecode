# 8. RAG — 우리 문서를 근거로

- LLM 한계와 RAG
- Indexing과 Retrieval
- VectorStore와 QuestionAnswerAdvisor
- 성능 튜닝과 한계

## 쉽게 말하면 — RAG

*Day 2 · RAG 심화*

- 모델에게 우리 문서를 찾아 읽히고 답하게 하는 것
- "오픈북 시험" 이라고 생각하면 쉽다
- 모델을 다시 학습시키지 않는다 — 문서만 갈아 끼운다

**이렇게 생각하면 쉽다 / 실제로는 / 왜 이렇게 하나**

| 이렇게 생각하면 쉽다 | 실제로는 | 왜 이렇게 하나 |
|---|---|---|
| 폐쇄형 시험 | 모델이 아는 것만으로 답 | 모르면 지어낸다 |
| 오픈북 시험 | RAG — 근거를 찾아 붙여 답 | 모르면 모른다고 할 수 있다 |
| 책에 미리 색인을 붙인다 | 인제스트(문서→조각→저장) | 찾는 속도가 빨라진다 |
| 질문에 맞는 쪽을 편다 | 검색(Retrieval) | 관련 조각 몇 개만 가져온다 |
| 출처 표시 | 근거 문서명·위치를 함께 | 틀렸을 때 어디를 고칠지 안다 |
| 교재가 바뀌면 | 교재만 바꾼다 | 문서 교체 vs 지식 갱신, 재학습 비용이 들지 않는다 |

> **지금은 이것만**: RAG는 오픈북 시험이다. 모델을 똑똑하게 만드는 게 아니라, 답이 적힌 페이지를 찾아서 같이 건네주는 것이다.

## 왜 RAG인가 — LLM의 두 한계

*Day 2 · RAG 심화*

- 지식 시점 — 학습 이후의 일과 우리 내부 문서는 모른다
- 환각 — 모르는 것도 그럴듯하게 지어낸다
- 해결: 질문마다 관련 근거를 찾아 함께 넣어 준다
  - 모델을 재학습하지 않는다 — 문서만 갈아 끼우면 최신이 된다

> **정리**: RAG = 검색(Retrieval) + 생성(Generation). 기억에 맡기지 않고 눈앞의 근거로 답하게 하는 것 — 최신성·정확성·출처가 필요한 실무의 핵심.

## RAG 전체 파이프라인

*Day 2 · RAG 심화*

- Indexing(사전 준비): 문서를 읽어 나누고 임베딩해 저장
- Retrieval(질문마다): 관련 조각을 찾아 질문과 함께 모델에

위(Indexing)는 사전 준비 — 문서 → Reader → Splitter → Embedding → VectorStore. 아래(Retrieval)는 질문마다 — 유사도 검색 → 질문+근거 → 모델 → 근거 있는 답.

```
Indexing:  문서 → Reader → Splitter → Embedding → VectorStore
Retrieval: 질문 → 유사도 검색 → 질문+근거 → ChatModel → 근거 있는 답
```

## 임베딩 모델 선택

*Day 2 · RAG 심화*

- 임베딩 모델은 한 번 정하면 바꾸기 어렵다 — 바꾸면 전량 재색인
- 차원이 크다고 항상 좋은 것은 아니다 — 저장·검색 비용이 함께 는다
- 한국어 성능은 모델마다 편차가 크다 — 우리 문서로 직접 확인

| 기준 | 무엇을 보나 | 실무 판단 |
|---|---|---|
| 차원 | 768 · 1024 · 1536 · 3072 | 클수록 정확·느리고 무겁다 |
| 한국어 | 우리 도메인 문서에서의 회수율 | 샘플 30건으로 직접 비교 |
| 최대 입력 | 한 번에 넣을 수 있는 길이 | 청크 크기의 상한이 된다 |
| 비용 | 100만 토큰당 단가 | 인제스트 1회 + 질의마다 1회 |
| 로컬 가능 | 자체 호스팅 여부 | 민감 문서는 외부 전송 자체가 문제 |
| 안정성 | 모델 폐기·버전 변경 | 바뀌면 전량 재색인이다 |

> **주의**: 임베딩 모델을 바꾸면 기존 벡터는 전부 무용지물이다. 차원이 같아도 의미 공간이 달라 섞이면 검색이 조용히 망가진다 — 오류도 안 난다.

## ① 문서 읽기 — DocumentReader

*Day 2 · RAG 심화*

- 다양한 형식을 Document 목록으로 읽어들인다
  - PDF·텍스트·마크다운·HTML 등 형식별 Reader

```java
// PDF를 페이지 단위 Document로 읽기
var reader = new PagePdfDocumentReader(
    "classpath:/handbook.pdf");
List<Document> docs = reader.get();
// Document = 본문 텍스트 + 메타데이터(출처·페이지 등)
```

## 쉽게 말하면 — 청킹

*Day 2 · RAG 심화*

- 긴 문서를 찾기 좋은 크기로 자르는 일
- 너무 크면 잡음이 섞이고, 너무 작으면 맥락이 끊긴다
- 잘린 자리에서 말이 끊기지 않게 조금 겹쳐 자른다

**이렇게 생각하면 쉽다 / 실제로는 / 잘못하면**

| 이렇게 생각하면 쉽다 | 실제로는 | 잘못하면 |
|---|---|---|
| 책을 단락 단위로 오려 둔다 | 청크 = 검색 단위 | 한 권을 통째로 주게 된다 |
| 오린 조각이 너무 크면 | 청크 1500자 이상 | 필요 없는 내용까지 딸려 온다 |
| 너무 작으면 | 청크 200자 이하 | 앞뒤 맥락이 잘려 뜻이 안 통한다 |
| 앞뒤 문장을 조금 겹쳐 자른다 | 겹침(overlap) 10~20% | 문장 중간이 잘려 근거가 반토막 |
| 문서 구조를 따라 자른다 | 문단·헤더 기준 분할 | 표나 코드가 엉뚱하게 쪼개진다 |

> **지금은 이것만**: 800~1200자에 10~20% 겹침으로 시작한다. 정답은 문서마다 다르다 — 시작값을 정해 두고, 실패 사례를 보면서 조정하면 된다.

## ② 텍스트 분할 — 청킹

*Day 2 · RAG 심화*

- 긴 문서를 의미 단위 조각(chunk)으로 나눈다
  - 너무 크면 검색이 뭉툭, 너무 작으면 맥락이 끊긴다 — 균형
- TokenTextSplitter 등으로 크기·겹침을 조절

```java
var splitter = new TokenTextSplitter();
List<Document> chunks = splitter.apply(docs);
// 각 chunk가 검색·주입의 단위가 된다
```

## 메타데이터 설계 — 무엇을 저장하나

*Day 2 · RAG 심화*

- 청크에 출처·버전·부서·유효기간을 붙여 두면 나중에 다 쓴다
- 출처 표기, 필터 검색, 만료 문서 제외가 전부 메타데이터에서 나온다
- 인제스트 때 안 넣으면 나중에 다시 넣을 수 없다 — 전량 재색인

```java
List<Document> docs = splitter.apply(reader.get()).stream()
    .map(doc -> {
        Map<String, Object> meta = new HashMap<>(doc.getMetadata());
        meta.put("source",    fileName);           // 출처 표기용
        meta.put("docType",   "handbook");         // 필터용
        meta.put("dept",      "CS");               // 권한·범위 제한용
        meta.put("version",   "2026-07");          // 최신본 판별용
        meta.put("validUntil", "2027-06-30");      // 만료 제외용
        return new Document(doc.getText(), meta);
    })
    .toList();
vectorStore.add(docs);
```

> **주의**: 메타데이터는 인제스트 시점에만 넣을 수 있다. 빠뜨리면 전체를 다시 색인해야 한다 — 처음부터 넉넉히 넣어 두는 편이 언제나 싸다.

## 청킹 전략 — 크기와 겹침 정하기

*Day 2 · RAG 심화*

- 청크 크기는 "질문 하나에 답할 만한 분량" 이 기준이다
- 너무 잘면 맥락이 끊기고, 너무 크면 잡음이 함께 딸려 온다
- 겹침(overlap)은 경계에서 잘린 문장을 구제한다

| 문서 유형 | 권장 크기 | 겹침 | 이유 |
|---|---|---|---|
| FAQ · Q&A | 300~500 토큰 | 10% | 한 항목이 곧 한 청크 |
| 규정 · 매뉴얼 | 600~900 토큰 | 15~20% | 조항 단위 · 앞뒤 참조가 있다 |
| 기술 문서 | 800~1200 토큰 | 20% | 코드·표가 잘리면 못 쓴다 |
| 회의록 · 대화 | 400~700 토큰 | 20% | 화자 전환이 경계 |
| 법률 · 계약 | 구조 기반 분할 | 조항 단위 | 크기보다 조항 경계가 우선 |

## ③ VectorStore — 저장과 검색

*Day 2 · VectorStore와 QA Advisor*

- 임베딩한 조각을 저장하고 유사도로 검색한다
  - pgvector·Redis·Chroma 등 — 같은 인터페이스로 교체 가능

질문을 임베딩해 가까운 조각을 top-k로 찾는다. 저장소가 pgvector든 다른 것이든 VectorStore 인터페이스는 같다 — 공급자 독립성이 여기도.

```
저장: 조각 → 임베딩 → VectorStore
검색: 질문 → 임베딩 → 유사도 top-k → 근거 조각
```

저장소가 pgvector든 무엇이든 인터페이스는 같다.

## pgvector — 설정 예시

*Day 2 · VectorStore와 QA Advisor*

- PostgreSQL + pgvector 확장을 벡터 저장소로 쓴다
  - 스타터 + application.yml로 VectorStore 빈이 자동 구성

```yaml
# docker: pgvector 확장이 켜진 PostgreSQL 실행
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true   # 테이블 자동 생성
        # dimensions: <임베딩 모델 차원에 맞춤>
```

## 벡터 DB — 무엇을 고를까

*Day 2 · VectorStore와 QA Advisor*

- 판단 기준은 성능보다 "우리 팀이 운영할 수 있는가"
- 이미 쓰는 DB에 확장을 얹는 것이 대체로 가장 싸다
- 코드는 VectorStore 인터페이스라 나중에 바꿔도 된다

| 선택지 | 강점 | 약점 | 적합 |
|---|---|---|---|
| pgvector | 이미 쓰는 PostgreSQL 그대로 | 초대량에선 튜닝 필요 | 대부분의 팀의 첫 선택 |
| Redis | 빠름 · 이미 캐시로 씀 | 메모리 비용 | 소~중규모 · 낮은 지연 |
| Elasticsearch | 키워드+벡터 하이브리드 | 운영 부담 | 검색이 핵심 기능일 때 |
| Chroma | 가볍고 시작이 쉽다 | 운영 기능 부족 | PoC · 로컬 개발 |
| Pinecone 등 SaaS | 운영 부담 없음 | 비용 · 데이터 외부 전송 | 인프라 인력이 없을 때 |

## 인덱스 — 왜 검색이 느려지나

*Day 2 · VectorStore와 QA Advisor*

- 벡터 검색은 기본적으로 전수 비교 — 문서가 늘면 선형으로 느려진다
- 인덱스(HNSW·IVF)는 정확도를 조금 내주고 속도를 크게 얻는다
- 인덱스를 안 만든 채 운영에 올리는 것이 흔한 실수다

| 방식 | 특징 | 정확도 | 언제 |
|---|---|---|---|
| 인덱스 없음 | 전수 비교(exact) | 100% | 1만 건 미만 · 개발 |
| HNSW | 그래프 탐색 · 빠름 | 높음(근사) | 대부분의 운영 환경 |
| IVFFlat | 군집 후 일부만 탐색 | 중간(근사) | 메모리가 빠듯할 때 |

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true
        index-type: HNSW            # 기본값 · 운영 권장
        distance-type: COSINE_DISTANCE
        dimensions: 1536            # 임베딩 모델과 반드시 일치해야 한다
```

> **주의**: dimensions가 임베딩 모델과 다르면 저장 시점에 오류가 나거나, 더 나쁘게는 엉뚱한 결과가 나온다. 모델을 바꿀 때 함께 바꿔야 하는 값이다.

## 인제스트 — 한 흐름으로

*Day 2 · VectorStore와 QA Advisor*

- 읽기 → 분할 → 저장을 하나의 파이프라인으로 실행

```java
@Service
class IngestService {
    private final VectorStore vectorStore;
    void ingest(String path) {
        var reader = new PagePdfDocumentReader(path);
        var splitter = new TokenTextSplitter();
        vectorStore.add(splitter.apply(reader.get()));
    }
}
```

## ETL 파이프라인 — 읽기·변환·쓰기

*Day 2 · RAG 심화*

- Spring AI의 인제스트는 읽기 → 변환 → 쓰기 세 단계로 정형화돼 있다
- 변환 단계에 분할·요약·키워드·메타데이터 보강을 끼워 넣는다
- 각 단계가 인터페이스라 교체·테스트가 쉽다

```java
@Service
class IngestPipeline {
    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    public void ingest(Resource file) {
        // ① Read — 확장자에 맞는 Reader (PDF·DOCX·HTML 은 Tika 가 처리)
        List<Document> raw = new TikaDocumentReader(file).get();

        // ② Transform — 분할 후 메타데이터 보강
        var splitter = TokenTextSplitter.builder().withChunkSize(800).build();
        var chunks = new KeywordMetadataEnricher(chatModel, 5)
                .apply(splitter.apply(raw));

        // ③ Write — 임베딩은 VectorStore 가 알아서 호출한다
        vectorStore.add(chunks);
    }
}
```

## 쉽게 말하면 — 검색과 근거

*Day 2 · VectorStore와 QA Advisor*

- 질문이 들어오면 관련 조각 몇 개를 찾아 프롬프트에 붙인다
- 몇 개를 붙일지가 top-k다
- 답이 이상하면 찾아온 근거부터 본다

**이렇게 생각하면 쉽다 / 실제로는 / 실무 요령**

| 이렇게 생각하면 쉽다 | 실제로는 | 실무 요령 |
|---|---|---|
| 질문에 맞는 페이지를 편다 | 유사도 검색 | 질문도 좌표로 바꿔 가까운 것을 찾는다 |
| 몇 페이지를 볼까 | top-k (보통 3~5) | 많이 볼수록 비싸고 잡음도 는다 |
| 비슷한 페이지만 몰릴 때 | MMR — 다양성 섞기 | 같은 내용이 중복되는 것을 막는다 |
| 볼 수 있는 책을 제한 | 메타데이터 필터 | 권한·부서 범위를 여기서 강제한다 |
| 답 안에 출처를 적는다 | sources 반환 | 신뢰와 추적이 가능해진다 |

> **지금은 이것만**: 답이 이상하면 모델을 의심하기 전에 찾아온 근거를 먼저 본다. 근거에 답이 없으면 프롬프트를 아무리 고쳐도 좋아지지 않는다 — 이 순서가 RAG 디버깅의 전부다.

## ④ QuestionAnswerAdvisor

*Day 2 · VectorStore와 QA Advisor*

- 검색 → 근거 주입 → 생성의 아래쪽 흐름을 대신 처리하는 Advisor
- ChatClient에 붙이기만 하면 평범한 질문이 RAG 질문이 된다

```java
ChatClient chat = builder
        .defaultAdvisors(
                new QuestionAnswerAdvisor(vectorStore))
        .build();
// 이제 이 한 줄이 자동으로 검색+근거 주입을 한다
String answer = chat.prompt().user(q).call().content();
```

## 쉽게 말하면 — 대화 메모리

*Day 2 · VectorStore와 QA Advisor*

- 모델은 기억하지 못한다 — 우리가 다시 들려준다
- "그거", "아까 그것" 을 알아듣게 하는 장치
- 오래된 대화는 잘라 내거나 요약한다

**이렇게 생각하면 쉽다 / 실제로는 / 주의**

| 이렇게 생각하면 쉽다 | 실제로는 | 주의 |
|---|---|---|
| 상담원의 메모지 | 대화 이력 저장 | 메모지가 없으면 매번 처음부터 |
| 앞 대화를 요약해 붙인다 | 이력을 프롬프트에 주입 | 길수록 비용이 는다 |
| 최근 몇 건만 본다 | 윈도우 (예: 최근 20건) | 무한히 쌓아 두지 않는다 |
| 오래된 것은 줄여 적는다 | 요약 메모리 | 핵심만 남기고 비용을 줄인다 |
| 손님별 메모지 분리 | 대화 ID(사용자+세션) | 섞이면 남의 대화가 보인다 |

> **지금은 이것만**: 메모리는 모델의 기억이 아니라 우리가 다시 들려주는 것이다. 그래서 길어질수록 비싸지고, 대화 ID를 잘못 만들면 남의 대화가 섞인다.

## RAG + 대화 메모리 결합

*Day 2 · VectorStore와 QA Advisor*

- Advisor는 여러 개를 겹쳐 쓸 수 있다
  - QA(근거) + Memory(대화 이력)를 함께 → 맥락 있는 문서 챗봇

```java
ChatClient chat = builder
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new QuestionAnswerAdvisor(vectorStore))
        .build();
```

## RAG 성능 튜닝 포인트

*Day 2 · VectorStore와 QA Advisor*

| 튜닝 지점 | 무엇을 조절하나 |
|---|---|
| 청크 크기·겹침 | 너무 크면 뭉툭, 작으면 맥락 끊김 — 의미 단위로 |
| top-k (가져올 개수) | 적으면 근거 부족, 많으면 잡음·비용 증가 |
| 메타데이터 필터 | 부서·기간 등으로 검색 범위를 좁혀 정확도 향상 |
| 출처 표시·검증 | 답에 근거 문서를 함께 내 검증 가능하게 |

> **주의**: RAG 실패는 대부분 모델이 아니라 검색 단계에서 난다. 답이 부실하면 먼저 "관련 조각을 제대로 찾아왔는지"부터 확인하라.

## 필터 표현식 — 검색 범위를 좁힌다

*Day 2 · VectorStore와 QA Advisor*

- 메타데이터 조건으로 먼저 거른 뒤 유사도를 계산한다
- 부서·문서종류·기간으로 좁히면 정확도와 속도가 함께 오른다
- 권한 분리(테넌트·부서)도 이 필터가 담당한다 — 보안 경계다

```java
// ① 검색 요청에 직접 필터를 건다
var results = vectorStore.similaritySearch(SearchRequest.builder()
        .query(question)
        .topK(5)
        .similarityThreshold(0.65)
        .filterExpression("docType == 'handbook' && dept in ['CS','CX']")
        .build());

// ② Advisor 에 걸어 두면 모든 RAG 질의에 자동 적용된다
var qa = QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(SearchRequest.builder()
                .topK(5)
                .filterExpression("validUntil >= '" + LocalDate.now() + "'")
                .build())
        .build();
```

## 정리 — 근거 있는 AI로

*Day 2 · VectorStore와 QA Advisor*

- Indexing(읽기·분할·임베딩·저장)은 사전에 한 번
- Retrieval은 QuestionAnswerAdvisor가 자동으로
- Advisor를 조합해 RAG + 메모리 챗봇으로 확장
- 품질은 검색 단계(청크·top-k·필터)에서 결정된다

## RAG 실패 진단표

*Day 2 · VectorStore와 QA Advisor*

- RAG가 잘 안 될 때 어디를 봐야 하는지가 절반이다
- 먼저 검색 결과를 눈으로 본다 — 여기서 대부분 갈린다
- 근거에 답이 없으면 프롬프트를 고쳐도 소용없다

| 증상 | 먼저 확인 | 원인 | 대응 |
|---|---|---|---|
| 아무것도 못 찾는다 | 인제스트됐는가 | 문서 미적재 · 파싱 실패 | 청크 수 확인 · Reader 교체 |
| 엉뚱한 문서가 온다 | 검색 결과 상위 5건 | 임계값이 낮다 · 청크가 크다 | threshold 조정 · 청크 조정 · 필터 |
| 관련 문서가 빠진다 | 질문 표현 | 질문-문서 어휘 차이 | HyDE · 질문 변환 · 하이브리드 |
| 근거는 맞는데 답이 틀림 | 시스템 프롬프트 | 근거를 안 쓰고 지어냄 | "근거 안에서만" 명시 |
| 출처가 안 나온다 | 응답 컨텍스트 | 꺼내는 코드가 없다 | RETRIEVED_DOCUMENTS 사용 |
| 같은 문장만 반복 | 청크 중복 | 재색인 없이 add 반복 | source 기준 삭제 후 재적재 |
| 느리다 | topK · 임베딩 호출 | topK 과다 · 인덱스 없음 | 재순위로 좁힘 · HNSW |

> **체크**: "검색 결과를 눈으로 봤는가?" — 이 질문에 아니오라면 아직 진단을 시작하지 않은 것이다. /retrieve 같은 엔드포인트를 하나 열어 두면 평생 쓴다.

## 미니 실습 — 재색인과 메타데이터 (20분)

*Day 2 · VectorStore와 QA Advisor*

- 오늘의 함정 둘을 직접 밟아 본다
- Day 2 실습의 예행연습이다
- ch07/rag를 그대로 쓴다

| 단계 | 무엇을 한다 | 확인 기준 |
|---|---|---|
| ① | /ch07/ingest/samples 실행 후 청크 수 확인 | 숫자를 적어 둔다 |
| ② | 한 번 더 실행하고 청크 수 재확인 | 늘지 않아야 정상(재색인) |
| ③ | /ch07/retrieve?q=... 로 근거를 눈으로 확인 | 출처·점수가 보인다 |
| ④ | /ch07/ask로 답변+출처 확인 | 인용된 출처가 ③과 일치하는가 |
| ⑤ | 문서에 없는 질문 던지기 | "확인되지 않습니다" 계열 응답 |
| ⑥ | 메타데이터 없이 인제스트해 보기 | 출처를 못 붙인다 — 왜 지금 넣어야 하는지 |

> **체크**: ②와 ⑥이 이 장의 두 함정이다. 중복 적재와 메타데이터 누락은 오류를 내지 않는다 — 검색 품질만 조용히 나빠져서 원인을 찾기가 가장 어렵다.

## 실습 코드 — 우리 팀 위키 Q&A (완성본)

*Day 2 · VectorStore와 QA Advisor*

- 회식 규정으로 묻고 답하는 봇을 만든다
- 두 번 넣어도 조각 수가 같아야 정상이다
- 답보다 근거를 먼저 찍어 본다

```java
@Service
class WikiRag {
    private final VectorStore store;
    private final ChatClient chat;

    void 넣기(String 파일명, String 본문) {                       // ① 인제스트
        var doc = new Document(본문, Map.of("source", 파일명, "version", "v1"));
        var 조각 = new TokenTextSplitter().apply(List.of(doc));
        store.delete(new FilterExpressionBuilder()                // ② 같은 출처를 지우고
                .eq("source", 파일명).build());
        store.add(조각);                                          //   다시 넣는다 = 재색인
        System.out.println(파일명 + " → " + 조각.size() + "조각");
    }

    String 묻기(String q) {
        var 근거 = store.similaritySearch(SearchRequest.builder() // ③ 먼저 찾는다
                .query(q).topK(3).similarityThreshold(0.5).build());
        근거.forEach(d -> System.out.printf(" 근거 %s (%.2f)%n",
                d.getMetadata().get("source"), d.getScore()));     //   눈으로 확인
        if (근거.isEmpty()) return "확인되지 않습니다.";            // ④ 없으면 모델을 안 부른다
        return chat.prompt()
                .system("아래 근거만 사용해 답한다. 없으면 '확인되지 않습니다'.")
                .user("[근거]%n%s%n[질문] %s".formatted(합치기(근거), q))
                .call().content();
    }
}
// 넣기("회식규정.md", "회식은 월 1회, 1인 3만원 이내...")  → 회식규정.md → 4조각
// 묻기("회식비 얼마까지 돼요?")  →  근거 회식규정.md (0.71)  →  "1인 3만원 이내입니다."
```

## 실행·테스트 — 위키 Q&A

*Day 2 · VectorStore와 QA Advisor*

- 인제스트를 두 번 실행해 본다 — 조각 수가 늘면 재색인이 필요하다
- 답보다 검색 결과를 먼저 확인한다
- 테스트는 출처 문서 이름이 함께 오는지 본다

```bash
# 1) 파일 위치와 문서
src/main/java/com/skala/lab8/WikiRag.java
src/main/resources/lab8-docs/회식규정.md · 휴가규정.md · 장비대여.md
#   → 실행: SpringAI_실습/08_위키QnA 폴더를 VS Code 로 열고 F5 (또는 ./gradlew bootRun)

# 2) 인제스트 — 두 번 실행해 조각 수를 비교한다
curl -X POST localhost:8080/lab8/ingest      # 회식규정.md → 4조각...
curl -X POST localhost:8080/lab8/ingest      # 같은 숫자여야 정상(재색인)

# 3) 검색부터 눈으로 — 답변보다 먼저 본다
curl 'localhost:8080/lab8/retrieve?q=회식비 얼마까지 돼요'
#   근거 회식규정.md (0.71) · 휴가규정.md (0.42)

# 4) 답변 — 출처가 함께 나오는지 확인
curl 'localhost:8080/lab8/ask?q=회식비 얼마까지 돼요'
#   "1인 3만원 이내입니다. [출처: 회식규정.md]"
curl 'localhost:8080/lab8/ask?q=우주 여행 지원되나요'
#   "확인되지 않습니다."            ← 지어내지 않아야 정상

# 5) 테스트 — 골든 세트로 굳힌다 (모델 호출이라 -Peval 로 분리)
@Test void 근거가_있으면_답하고_없으면_거절한다() {
    assertThat(rag.묻기("회식비 얼마까지 돼요")).contains("3만원");
    assertThat(rag.묻기("우주 여행 지원되나요")).contains("확인되지");
}

# 안 되면 — 빈 검색: 임베딩 설정 · 중복 증가: 재색인 누락 · 지어냄: 거절 지시 추가
```

## 핵심 요약 — RAG 기본

*Day 2 · VectorStore와 QA Advisor*

- 이 장의 결론 — 모르는 것을 모른다고 답하게 만드는 것이 RAG 다
- 인덱싱은 미리, 검색은 질문마다

| 단계 | 한 줄 정리 | 실무 포인트 |
|---|---|---|
| 문서 읽기 | Tika 하나로 PDF·DOCX·HTML | 읽히는지부터 확인하고 시작 |
| 분할 | 질문 하나에 답할 분량이 기준 | 너무 잘면 맥락이, 크면 잡음이 는다 |
| 메타데이터 | 출처·부서·버전·유효기간 | 인제스트 때 안 넣으면 못 넣는다 |
| 재색인 | 같은 문서는 지우고 다시 | 안 하면 같은 청크가 쌓인다 |
| QA Advisor | 검색·근거 주입을 대신 처리 | 한 줄로 RAG가 켜진다 |
| 출처 표기 | 응답 컨텍스트에서 꺼낸다 | 출처 없는 답은 검증할 수 없다 |

> **체크**: 품질이 안 나오면 검색 결과부터 눈으로 확인해야 한다. 근거에 답이 없으면 프롬프트를 고쳐도 소용없다.
