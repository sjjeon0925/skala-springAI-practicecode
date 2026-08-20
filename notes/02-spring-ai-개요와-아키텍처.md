# 2. Spring AI 개요와 아키텍처

- 왜 Spring AI인가
- 전체 아키텍처와 설계 원칙
- 3대 핵심 추상화
- 공급자 독립성

## 쉽게 말하면 — LLM과 토큰

*Day 1 · 개요와 아키텍처*

- LLM은 다음에 올 말을 확률로 고르는 프로그램이다
- 글자가 아니라 토큰 단위로 읽고 쓴다
- 토큰이 곧 돈과 길이 제한이다

### 이렇게 생각하면 쉽다 / 실제로는 / 그래서

| 이렇게 생각하면 쉽다 | 실제로는 | 그래서 |
|---|---|---|
| 말을 아주 잘 잇는 사람 | 다음 토큰을 확률로 예측 | 그럴듯한데 틀릴 수 있다 |
| 글을 조각내서 읽는다 | 토큰 — 한글은 글자당 1~2개 | 길이와 비용을 토큰으로 센다 |
| 한 번에 읽을 수 있는 분량 | 컨텍스트 윈도 | 넘치면 앞부분부터 잘린다 |
| 말투를 정해 주는 첫 지시 | 시스템 메시지 | 여기서 역할과 규칙을 정한다 |
| 기억력이 없다 | 매번 대화를 다시 보낸다 | 이력이 길수록 비싸진다 |

> **지금은 이것만**: 모델은 기억하지 않는다 — 매번 다시 읽는다. 그래서 대화가 길어지면 느려지고 비싸진다. 이 사실 하나가 뒤에 나오는 메모리·비용 이야기의 전부다.

## Spring AI란 무엇인가

*Day 1 · 개요와 아키텍처*

- AI 모델을 Spring Boot 답게 다루게 해 주는 공식 프레임워크
  - HTTP 호출·JSON 파싱·인증을 직접 다루지 않는다
- 익숙한 의존성 주입·AutoConfiguration·빈 위에서 AI를 쓴다
  - ChatClient 같은 빈을 주입받아 메서드 호출로 끝
- Portable API — 공급자가 달라도 코드는 같다

## LLM 기본 — 토큰·컨텍스트·확률

*Day 1 · 개요와 아키텍처*

- 모델은 다음 토큰을 확률로 고른다 — 이 한 문장이 모든 특성의 원인이다
- 토큰이 비용·속도·한계의 단위다 — 글자도 단어도 아니다
- 컨텍스트 창을 넘으면 앞부분이 잘린다 — 대화가 길어질 때의 문제

| 개념 | 무엇인가 | 왜 알아야 하나 |
|---|---|---|
| 토큰 | 모델이 다루는 텍스트 조각(한글 약 1~2자) | 비용·속도·상한이 모두 토큰 단위 |
| 컨텍스트 창 | 한 번에 넣을 수 있는 토큰 총량 | 넘으면 잘린다 — 메모리·RAG 설계의 제약 |
| temperature | 다음 토큰 선택의 무작위성 | 0이면 거의 고정, 높으면 매번 다름 |
| 확률적 생성 | 같은 입력에 다른 출력이 가능 | 테스트를 답 내용으로 하면 안 되는 이유 |
| 환각 | 모르는 것도 그럴듯하게 만든다 | RAG·출처 표기가 필요한 이유 |
| 지식 컷오프 | 학습 시점 이후를 모른다 | |

## 전체 아키텍처 한눈에

*Day 1 · 개요와 아키텍처*

- 내 코드는 추상화(ChatClient·ChatModel)에만 의존한다
- 공급자 선택은 의존성 + application.yml — Spring Boot가 자동 구성

애플리케이션 → ChatClient → 추상화 → 공급자. 공급자는 설정으로 갈아 끼운다 — Spring Boot AutoConfiguration이 빈을 자동 구성한다.

| 애플리케이션 | ChatClient | 추상화(ChatModel 등) | 공급자 |
|---|---|---|---|

공급자 교체 시: 의존성 한 줄 + application.yml — 코드는 그대로

## 핵심 설계 원칙

*Day 1 · 개요와 아키텍처*

| 원칙 | 의미 |
|---|---|
| Portability | 공급자가 달라도 같은 인터페이스로 쓴다 |
| 추상화 우선 | 구현이 아니라 인터페이스(ChatModel 등)에 의존 |
| Spring Boot 통합 | 빈·DI·AutoConfiguration·Boot 스타터를 그대로 활용 |
| 확장성 | Advisor·Tool 등 끼워 넣는 지점을 표준화 |

## 3대 핵심 추상화

*Day 1 · 3대 핵심 추상화*

- Spring AI의 뼈대는 세 인터페이스 — ChatModel · EmbeddingModel · VectorStore
- 이 셋의 조합이 챗봇·검색·RAG·에이전트로 확장된다

ChatModel(대화) · EmbeddingModel(의미 벡터) · VectorStore(유사도 검색). 공급자가 달라도 인터페이스는 같다 — 셋을 조합해 대부분의 AI 기능을 만든다.

| 추상화 | 역할 |
|---|---|
| ChatModel | 프롬프트 → 텍스트 응답 · 대화·생성·요약 |
| EmbeddingModel | 텍스트 → 의미 벡터 · 검색과 RAG의 준비물 |
| VectorStore | 벡터 저장·유사도 검색 · 근거 문서를 찾는다 |

셋을 조합하면 — RAG · 챗봇 · 문서 검색 · 에이전트

## ① ChatModel — 대화의 기본

*Day 1 · 3대 핵심 추상화*

- 가장 기본이 되는 추상화 — 프롬프트 → 텍스트 응답
- 공급자별 구현(OpenAI·Anthropic·Azure OpenAI …)을 같은 인터페이스로
- 저수준 API — 실무에선 대개 ChatClient로 감싸서 쓴다

```java
@Service
public class SummaryService {
    private final ChatModel chatModel;   // 생성자 주입
    public String summarize(String text) {
        Prompt prompt = new Prompt("요약해줘:\n" + text);
        return chatModel.call(prompt)
            .getResult().getOutput().getText();
    }
}
```

## ② EmbeddingModel — 의미를 벡터로

*Day 1 · 3대 핵심 추상화*

- 텍스트를 의미를 담은 숫자 목록(벡터)으로 바꾼다
  - 뜻이 비슷하면 벡터도 가깝다 — 의미 기반 검색의 토대
- 검색·RAG·분류·군집의 준비물이 된다

```java
@Service
public class EmbedService {
    private final EmbeddingModel embeddingModel;
    public float[] embed(String text) {
        return embeddingModel.embed(text);   // 의미 벡터
    }
}
```

## 쉽게 말하면 — 임베딩과 벡터

*Day 1 · 3대 핵심 추상화*

- 문장을 숫자 배열로 바꾸는 것이 임베딩이다
- 뜻이 가까우면 숫자도 가깝다
- 그래서 단어가 달라도 찾을 수 있다

### 이렇게 생각하면 쉽다 / 실제로는 / 왜 좋은가

| 이렇게 생각하면 쉽다 | 실제로는 | 왜 좋은가 |
|---|---|---|
| 지도 위의 좌표 | 임베딩 — 문장 → 숫자 배열 | 위치가 가까우면 뜻도 가깝다 |
| 두 좌표 사이의 거리 | 코사인 유사도(0~1) | 0.8이면 비슷, 0.2면 다르다 |
| 강아지 ↔ 반려견 | 값이 0.8 이상 | 단어가 달라도 찾힌다 |
| 강아지 ↔ 주식투자 | 값이 0.3 안팎 | 엉뚱한 것은 안 걸린다 |
| 좌표를 모아 둔 지도책 | 벡터 DB(VectorStore) | 질문과 가까운 문단을 찾아 준다 |

> **지금은 이것만**: 뜻이 가까우면 숫자도 가깝다. 이 한 문장이 검색(RAG)의 전부다 — 키워드가 하나도 안 겹쳐도 찾아 주는 이유가 여기에 있다.

## ③ VectorStore — 유사도 검색

*Day 1 · 3대 핵심 추상화*

- 벡터를 저장하고, 질문 벡터와 가까운 조각을 검색한다
- pgvector·Redis·Chroma 등 여러 저장소를 같은 인터페이스로

질문을 임베딩해 벡터 공간에서 가까운 문서 조각을 찾는다. "휴가 내는 법"으로 물어도 "연차 신청" 규정을 찾아내는 의미 검색.

| 질문 | 임베딩 | 벡터 공간에서 가까운 조각 | 근거 문서 |
|---|---|---|---|

"휴가 내는 법"으로 물어도 "연차 신청" 규정을 찾아낸다 — 의미 검색

## 공급자 독립성 — 코드는 그대로

*Day 1 · 3대 핵심 추상화*

- 같은 ChatClient/추상화 코드로 여러 공급자를 교체할 수 있다
- 개발은 소형 모델, 운영은 고성능 모델 — 설정만 바꾼다

비즈니스 코드는 한 줄도 바뀌지 않는다. 스타터 의존성과 application.yml만 교체하면 공급자가 바뀐다.

| 내 코드 | ChatClient·추상화에만 의존 — 한 줄도 바뀌지 않는다 |
|---|---|
| 바뀌는 것 | 스타터 의존성과 application.yml 뿐 |
| 그래서 | 개발은 소형 모델 · 운영은 고성능 모델 |

## 공급자별 옵션 — 공통과 고유

*Day 1 · 개요와 아키텍처*

- 공통 옵션(model·temperature·maxTokens)은 ChatOptions로 동일하게
- 공급자 고유 옵션은 각자의 XxxChatOptions로만 지정한다
- 고유 옵션을 쓰는 순간 그 코드는 그 공급자에 묶인다 — 경계를 알고 쓰자

| 구분 | 공통(ChatOptions) | 공급자 고유(예) |
|---|---|---|
| 모델 선택 | model | OpenAiChatOptions.model |
| 창의성 | temperature · topP | frequencyPenalty · presencePenalty |
| 길이 제한 | maxTokens | OpenAI: maxCompletionTokens |
| 출력 형식 | — | OpenAI: responseFormat(JSON Schema) |
| 추론 강도 | — | Anthropic: thinking / OpenAI: reasoningEffort |

## 미니 실습 — 3대 추상화 확인

*Day 1 · 개요와 아키텍처*

- 설명을 들었으니 숫자로 확인한다
- 세 추상화가 정말 그렇게 도는지 본다
- 키가 없으면 ①④만 해도 된다

| 단계 | 무엇을 한다 | 확인 기준 |
|---|---|---|
| ① | SpringAI실습/00_참조예제 실행 (F5 또는 `./gradlew bootRun`) | 기동 로그에 포트 8080 |
| ② | `/ch01/similarity?a=강아지&b=반려견` | 0.8 이상 — 뜻이 가까우면 값도 가깝다 |
| ③ | `/ch01/similarity?a=강아지&b=주식투자` | 0.3 안팎 — 먼 개념은 값도 멀다 |
| ④ | build.gradle에서 모델 스타터 한 줄 찾기 | 이 줄이 곧 공급자 독립성이다 |
| ⑤ | `/ch03/ask?q=...` 로 ChatModel 호출 | 응답 한 줄 (키 필요) |
| ⑥ | `/actuator/metrics/ai.tokens` 확인 | 호출한 만큼 토큰이 쌓인다 |

## 실습 코드 — 내 말과 닮은 속담 찾기

*Day 1 · 개요와 아키텍처*

- 임베딩이 숫자로 보이는 순간이다
- `?q=조심해서 나쁠 건 없지` → "돌다리도 두들겨..."가 1등
- 결과 숫자를 적어 두면 8장에서 다시 쓴다

```java
@RestController
class ProverbLab {
    private final EmbeddingModel embedding;              // ① 뜻을 숫자로 바꾸는 도구
    ProverbLab(EmbeddingModel embedding) { this.embedding = embedding; }
    static final List<String> 속담 = List.of(
        "티끌 모아 태산", "돌다리도 두들겨 보고 건너라",
        "원숭이도 나무에서 떨어진다", "가는 말이 고와야 오는 말이 곱다");
    @GetMapping("/lab1/proverb")                        // GET ?q=조심해서 나쁠 건 없지
    Map<String, Double> match(@RequestParam String q) {
        float[] 내문장 = embedding.embed(q);              // ② 내 문장 → 숫자 배열
        Map<String, Double> 점수 = new LinkedHashMap<>();
        for (String p : 속담)
            점수.put(p, cosine(내문장, embedding.embed(p))); // ③ 속담과 거리 재기
        return 점수;
    }
    static double cosine(float[] a, float[] b) {          // 두 화살표가 이루는 각도
        double dot=0, na=0, nb=0;
        for (int i=0;i<a.length;i++){ dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i]; }
        return dot / (Math.sqrt(na)*Math.sqrt(nb));       // 1에 가까울수록 비슷
    }
}
// 결과 예: 돌다리도...(0.62) · 원숭이도...(0.21) · 티끌모아...(0.18)
```

## 실행·테스트 — 속담 유사도

*Day 1 · 개요와 아키텍처*

- 키가 필요한 첫 실습이다 — export 한 줄을 먼저 확인하고 시작한다
- 속담 두 개의 유사도가 숫자로 찍히면 임베딩이 살아 있는 것이다
- 테스트는 값이 아니라 벡터 차원부터 확인한다

```bash
# 1) 파일 위치
src/main/java/com/skala/lab1/ProverbLab.java
#    → 실행: SpringAI_실습/02_속담유사도 폴더를 VS Code 로 열고 F5 (또는 ./gradlew bootRun)

# 2) 호출 — 세 문장을 차례로 넣어 본다
curl 'localhost:8080/lab1/proverb?q=조심해서 나쁠 건 없지'
curl 'localhost:8080/lab1/proverb?q=작은 돈도 모으면 커진다'
curl 'localhost:8080/lab1/proverb?q=오늘 점심 뭐 먹지'        # 전부 낮게 나와야 정상

# 3) 기대 결과
{"돌다리도 두들겨 보고 건너라":0.62, "원숭이도...":0.21, "티끌 모아 태산":0.18}

# 4) 테스트로 굳히기 — 값이 아니라 '순서'를 검증한다
@Test void 뜻이_가까운_속담이_1등이다() {
    var r = lab.match("조심해서 나쁠 건 없지");
    String top = r.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    assertThat(top).contains("돌다리");        // 점수는 모델 버전마다 조금씩 달라진다
}

# 안 되면 — 401: 키 오류 · 빈 결과: 임베딩 모델 설정 · 429: 잠시 후 재시도
```

## 핵심 요약 — 개요와 3대 추상화

*Day 1 · 개요와 아키텍처*

- 2장의 결론 — 내 코드는 추상화에만 의존하고, 공급자는 설정으로 바꾼다
- 뒤에 나오는 모든 기능이 이 세 인터페이스의 조합이다

| 개념 | 한 줄 정리 | 실무 포인트 |
|---|---|---|
| Spring AI | AI 위에 얹은 Spring Boot 표준 추상화 | 반복과 종속을 걷어 내는 것이 목적 |
| ChatModel | 프롬프트 → 텍스트, 가장 낮은 계층 | 실무에선 ChatClient로 감싸 쓴다 |
| EmbeddingModel | 텍스트 → 의미 벡터 | 검색·RAG·분류의 재료 |
| VectorStore | 벡터를 저장하고 유사도로 찾는다 | 운영은 pgvector 등 영속 스토어 |
| 공급자 독립성 | 인터페이스는 같고 구현만 다르다 | 공통 옵션만 쓰면 교체가 무료 |
| AutoConfiguration | 스타터 + yml 이면 빈이 자동 구성 | 코드가 아니라 설정으로 바꾼다 |

> **체크**: "공급자를 바꾸려면 무엇을 고쳐야 하나?" — 의존성과 yml 뿐이라고 답할 수 있으면 된다.
