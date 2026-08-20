# 4. 의존성·설정과 ChatClient

- BOM과 스타터
- application.yml과 Profile
- 첫 앱 HelloAI
- ChatClient 기본기

## 시작하기 · BOM과 스타터

*Day 1 · 의존성과 설정*

- BOM으로 Spring AI 모듈들의 버전을 한 번에 맞춘다
- 스타터를 넣으면 해당 공급자 연동이 자동 구성된다
  - 공급자를 바꾸려면 스타터만 교체 · 코드는 그대로

```gradle
// build.gradle — 의존성
dependencies {
    implementation platform(
        "org.springframework.ai:spring-ai-bom:2.0.0")   // 버전 일괄 관리
    implementation "org.springframework.ai:spring-ai-starter-model-openai"
}
```

## Spring AI 모듈 지도 · 무엇을 넣나

*Day 1 · 의존성과 설정*

- 아티팩트 이름에 규칙이 있다 · 이름만 보면 역할을 안다
- spring-ai-starter-* 는 자동 구성 포함, 나머지는 라이브러리만
- 필요한 것만 넣는다 · 스타터 하나가 빈 수십 개를 만든다

| 아티팩트 패턴 | 역할 | 예 |
|---|---|---|
| spring-ai-starter-model-* | 모델 공급자 연동 + 자동 구성 | -model-openai, -model-anthropic |
| spring-ai-starter-vector-store-* | 벡터 저장소 연동 + 자동 구성 | -pgvector, -redis |
| spring-ai-starter-mcp-* | MCP 클라이언트/서버 | -mcp-client, -mcp-server-webmvc |
| spring-ai-advisors-vector-store | QuestionAnswerAdvisor 등 | RAG Advisor |
| spring-ai-rag | 모듈형 RAG 파이프라인 | 질문 변환·재순위 |
| spring-ai-*-document-reader | 문서 읽기 | tika, pdf, markdown |
| spring-ai-bom | 버전 일괄 관리 | 여기 하나만 버전을 적는다 |

## application.yml · 기본 설정

*Day 1 · 의존성과 설정*

- 공급자 설정은 application.yml에 선언 · 코드가 아니다
- API 키는 환경변수로 주입한다 · 소스에 넣지 않는다

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}    # 환경변수에서 주입
      chat:
        options:
          temperature: 0.7          # 응답 다양성(예시값)
```

> **주의**: API 키를 소스·깃에 커밋하지 말 것. 환경변수·시크릿 매니저로 주입한다. 유출된 키는 곧 비용·보안 사고로 이어진다.

## 프로파일 전환 · 개발과 운영을 나눈다

*Day 1 · 의존성과 설정*

- 같은 코드로 환경만 바꿔 돈다
- 개발은 싼 모델·짧은 응답, 운영은 성능과 안정성
- 키는 환경변수로 · 파일에 적지 않는다

```yaml
# application.yml — 공통(모델 키는 환경변수로만)
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

# application-dev.yml — 개발: 싸고 빠르게
spring:
  ai:
    openai:
      chat.options:
        model: gpt-4o-mini        # 소형 모델
        temperature: 0.0          # 결과를 재현 가능하게
        max-tokens: 300           # 실수로 길게 쓰지 않도록

# application-prod.yml — 운영: 품질과 안정성
spring:
  ai:
    openai:
      chat.options:
        model: gpt-4o             # 고성능 모델
        temperature: 0.2
```

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
# 코드는 그대로, 설정만 바뀐다
```

## 설정 우선순위 · 무엇이 이기나

*Day 1 · 의존성과 설정*

- 같은 설정이 여러 곳에 있으면 정해진 순서로 덮어쓴다
- "분명히 바꿨는데 안 먹는다"는 대부분 이 순서를 몰라서 생긴다
- 운영에서는 환경변수·시크릿이 파일을 이긴다 · 의도된 설계다

| 순위 | 출처 | 실무에서 |
|---|---|---|
| 1 (가장 셈) | 커맨드라인 인자 `--spring.ai...` | 일회성 실험 |
| 2 | OS 환경변수 `SPRING_AI_OPENAI_API_KEY` | 컨테이너·K8s의 기본 |
| 3 | application-{profile}.yml | 환경별 차이만 담는다 |
| 4 | application.yml | 공통 기본값 |
| 5 (가장 약함) | 코드의 @Value 기본값 | 최후의 안전망 |

```bash
# 환경변수 이름 규칙 — 점·하이픈을 밑줄로, 대문자로
#   spring.ai.openai.api-key  →  SPRING_AI_OPENAI_API_KEY
#   helpdesk.rag.top-k        →  HELPDESK_RAG_TOPK
```

## AutoConfiguration · 무엇이 자동인가

*Day 1 · 의존성과 설정*

- 스타터 + 설정만 있으면 Spring Boot가 필요한 빈을 자동 등록한다
  - ChatModel, ChatClient.Builder, EmbeddingModel 등
- 나는 그 빈을 주입받아 쓰기만 하면 된다 · 배선 코드가 없다

application.yml과 스타터를 근거로 Spring Boot가 AI 빈을 자동 구성한다. 개발자는 배선을 신경 쓰지 않고 추상화 빈을 주입받아 쓴다.

스타터 의존성 + application.yml → Spring Boot 자동 구성 → AI 빈(ChatModel · ChatClient.Builder · EmbeddingModel) → 주입받아 쓰기만 하면 된다

## 프로젝트 구조 · 패키지 나누기

*Day 1 · 의존성과 설정*

- AI 관련 코드를 한 덩어리로 몰아두지 않는다 · 역할별로 나눈다
- config(빈 구성) · service(업무) · tools(행동) · rag(근거)로 분리
- 컨트롤러는 AI를 모른다 · 서비스 인터페이스만 본다

```
com.skala.ai
├─ config/        AiConfig.java          // ChatClient·Advisor·VectorStore 빈
├─ web/           ChatController.java    // REST·SSE 엔드포인트
├─ service/       AssistantService.java  // 업무 흐름(프롬프트 조립·호출)
├─ rag/           IngestService.java     // 문서 인제스트
│                 RetrievalService.java  // 검색·근거 구성
├─ tools/         OrderTools.java        // @Tool 정의(행동)
├─ advisor/       AuditAdvisor.java      // 공통 관심사(로깅·안전)
└─ dto/           AnswerDto.java         // 구조화 출력용 record
```

## 쉽게 말하면 · 빌더와 점(.) 이어 쓰기

*Day 1 · 의존성과 설정*

- 앞으로 나올 코드는 점(.)을 계속 찍는 모양이다 · 먼저 이것부터 익힌다
- 어려운 문법이 아니다 · 주문서에 옵션을 체크하는 것과 같다
- 규칙은 하나 · 마지막 한 번에서야 실제 일이 벌어진다

| 이렇게 생각하면 쉽다 | 실제로는 | 왜 그렇게 하나 |
|---|---|---|
| 햄버거 주문서 | 빌더(Builder) | 필요한 옵션만 골라 담는다 |
| "치즈 추가, 양파 빼고, 포장이요" | 메서드 체이닝 | 한 문장으로 이어서 말한다 |
| 점원이 주문서를 다시 건네준다 | 메서드가 자기 자신을 돌려준다 | 그래서 점을 또 찍을 수 있다 |
| 마지막에 "주문할게요" | build() · call() | 이때 비로소 만들어지고 불린다 |
| 주문서만 쓰고 안 내면 | 종료 메서드를 빠뜨림 | 아무 일도 안 난다 · 흔한 실수 |
| 말하듯 읽히는 주문 | Fluent API | 그렇게 읽히도록 설계한 결과다 |

## 빌더 · 체이닝 · Fluent API

*Day 1 · 의존성과 설정*

- 세 가지는 별개가 아니다 · 빌더를 체이닝으로 쓰면 Fluent API가 된다
- 빌더는 만드는 법 · 체이닝은 이어 쓰는 문법 · Fluent는 그렇게 읽히도록 한 설계
- Spring AI 코드가 문장처럼 보이는 이유가 이것이다

```java
// ① 생성자로만 만들면 — 인자가 늘수록 못 읽는다
var chat = new Chat("gpt-4o-mini", 0.0, 300, "너는 상담원이다", true, null);
//                                       ↑ 무엇이 무엇인지 알 수 없다

// ② 빌더(Builder) — 이름을 붙여, 필요한 것만 담는다
ChatOptions o = ChatOptions.builder()
    .model("gpt-4o-mini")   // 각 메서드가 자기 자신(this)을 돌려준다
    .temperature(0.0)       //   → 그래서 점을 또 찍을 수 있다 = 메서드 체이닝
    .maxTokens(300)
    .build();               // ③ 종료 메서드 — 여기서 객체가 실제로 만들어진다

// ④ Fluent API — 체이닝으로 '문장처럼' 읽히게 설계한 API
String answer = chat.prompt()          // 시작
    .system("너는 상담원이다")          // 중간 — 무엇을 얹을지
    .user("반품 규정 알려줘")
    .call()                            // 종료 — 여기서 비로소 모델을 부른다
    .content();                        // 결과 꺼내기

// build() · call() 을 빠뜨리면? 아무 일도 일어나지 않는다. 가장 흔한 실수다.
```

## 쉽게 말하면 · ChatClient

*Day 1 · 의존성과 설정*

- 모델을 부르는 표준 창구다
- 미리 말투와 옵션을 정해둔 창구를 만든다
- 코드는 창구에 말만 건다

| 이렇게 생각하면 쉽다 | 실제로는 | 이득 |
|---|---|---|
| 부서별 전화 창구 | 용도별 ChatClient 빈 | 요약용·분류용을 따로 둔다 |
| 창구마다 정해진 응대 지침 | 기본 시스템 프롬프트 | 매번 지시를 다시 안 써도 된다 |
| 말 빠르기·길이 규정 | 온도·최대 토큰 | 답이 매번 달라지지 않는다 |
| 창구를 바꿔도 절차는 같다 | 모델 교체는 설정 한 줄 | 코드를 안 고친다 |
| 통화 기록 | 응답 메타데이터(토큰 등) | 비용을 볼 수 있다 |

> **지금은 이것만**: 모델을 직접 부르지 말고 창구(ChatClient)를 하나 만들어 두고 쓴다. 그 창구에 말투와 옵션을 미리 정해 두면, 부르는 쪽 코드는 훨씬 단순해진다.

## ChatClient 빈 · 용도별로 나눈다

*Day 1 · 의존성과 설정*

- 하나의 ChatClient로 모든 일을 시키면 기본값이 서로 충돌한다
- 요약용·분류용·상담용처럼 용도별 빈을 만들어 이름으로 주입
- 각 빈은 자기 시스템 프롬프트·옵션·Advisor를 갖는다

```java
@Configuration
class AiConfig {
    @Bean // 분류·추출 — 흔들리면 안 되는 일
    ChatClient extractClient(ChatClient.Builder b) {
        return b.defaultSystem("너는 정확한 추출기다. 추측하지 말고 없으면 null.")
                .defaultOptions(ChatOptions.builder().temperature(0.0).build())
                .build();
    }

    @Bean // 상담 — 자연스러움이 중요한 일
    ChatClient supportClient(ChatClient.Builder b) {
        return b.defaultSystem("너는 친절한 고객 상담원이다.")
                .defaultOptions(ChatOptions.builder().temperature(0.7).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
```

## HelloAI · 첫 번째 앱

*Day 1 · 의존성과 설정*

- Controller에서 ChatClient.Builder를 주입받아 빌드
- 질문을 받아 `.prompt().user(q).call().content()`로 응답

```java
@RestController
class HelloAiController {
    private final ChatClient chat;

    HelloAiController(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    @GetMapping("/ai")
    String ask(@RequestParam String q) {
        return chat.prompt().user(q).call().content();
    }
}
```

## ChatClient vs ChatModel

*Day 1 · ChatClient*

| 구분 | ChatModel | ChatClient |
|---|---|---|
| 계층 | 저수준 추상화 | 그 위의 Fluent API |
| 호출 | `call(Prompt)` | `.prompt().user()...call()` |
| 기능 | 요청→응답 | + Advisor · 옵션 · 객체 변환 |
| 실무 | 직접 쓰는 일 드묾 | 대부분 이걸 쓴다 |

## ChatClient 기본 사용법

*Day 1 · ChatClient*

- 체인 한 줄에 구성 → 호출 → 결과 변환이 담긴다
- `.call()`은 동기 응답, 결과는 `.content()`/`.entity()`로 꺼낸다

prompt() → advisors() → options() → 모델 호출 → 결과 변환. 한 체인 안에 프롬프트 구성부터 객체 변환까지 이어진다.

prompt() · advisors() · options() · call() · content()/entity() — 한 체인 안에 프롬프트 구성부터 객체 변환까지

## 세 가지 호출 방식 · 무엇을 언제

*Day 1 · ChatClient*

- `call()` 동기 · `stream()` 스트리밍 · 화면 형태가 선택을 정한다
- 긴 답변을 동기로 받으면 수 초간 빈 화면이 된다
- 출처·구조화 응답이 필요하면 동기가 다루기 쉽다

| 방식 | 반환 | 적합 | 주의 |
|---|---|---|---|
| `.call().content()` | String | 짧은 답 · 분류 · 추출 | 긴 답변은 체감이 나쁘다 |
| `.call().entity(T)` | 객체 | 구조화 응답 API | 형식 실패 대비 필요 |
| `.call().chatClientResponse()` | 응답+컨텍스트 | 출처·메타데이터 필요 | 가장 정보가 많다 |
| `.stream().content()` | Flux\<String\> | 채팅 UI · 긴 생성 | 취소·타임아웃 필수 |

```java
String text   = chat.prompt().user(q).call().content();
Ticket ticket = chat.prompt().user(q).call().entity(Ticket.class);
var full      = chat.prompt().user(q).call().chatClientResponse();
Flux<String> s = chat.prompt().user(q).stream().content();
```

## 빌더 패턴 · 공통 기본값

*Day 1 · ChatClient*

- ChatClient.Builder로 공통 기본값을 미리 심어 빈으로 등록
  - 기본 시스템 메시지·기본 옵션·기본 Advisor 등
- 이후 호출은 필요한 것만 얹으면 된다 · 중복 제거

```java
@Configuration
class AiConfig {
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("너는 친절한 고객 상담원이다.")
                .build();
    }
}
```

## 메시지 역할 · System과 User

*Day 1 · ChatClient*

- 프롬프트는 역할이 다른 메시지의 묶음이다
  - System → 규칙·말투·페르소나, User → 이번 질문

System은 역할·규칙을, User는 이번 질문을 담는다. 대화 이력은 Advisor가 자동 주입 · 역할을 나누면 프롬프트가 재사용·관리하기 쉽다.

- System: 역할·규칙·말투 — 매번 같다
- User: 이번 질문 — 매번 다르다
- 대화 이력: Advisor가 자동으로 주입한다

## 결과 받기 · content · entity

*Day 1 · ChatClient*

- `.content()` — 그냥 문자열 응답
- `.entity(Xxx.class)` — 타입 안전한 객체로 변환(구조화 출력)
- `.chatResponse()` — 메타데이터까지 포함한 전체 응답

```java
// 1) 문자열
String text = chat.prompt().user(q).call().content();

// 2) 객체 (구조화 출력) — Day 2에서 자세히
record Answer(String summary, List<String> keywords) {}
Answer a = chat.prompt().user(q).call().entity(Answer.class);
```

## 응답 메타데이터 · 무엇이 함께 오나

*Day 1 · ChatClient*

- 응답에는 텍스트 말고도 운영에 필요한 정보가 함께 온다
- Usage(토큰)·finishReason(끝난 이유)·모델명이 대표적
- finishReason이 length 면 답이 잘린 것이다 · 그냥 넘기면 사고

```java
ChatResponse response = chat.prompt().user(q).call().chatResponse();

// ① 본문
String text = response.getResult().getOutput().getText();

// ② 왜 끝났나 — stop(정상) · length(잘림) · tool_calls(도구 호출)
String finishReason = response.getResult().getMetadata().getFinishReason();
if ("length".equalsIgnoreCase(finishReason)) {
    log.warn("응답이 maxTokens 에서 잘렸다 — 상한을 올리거나 요약을 시키자");
}

// ③ 얼마나 썼나 — 비용 계산의 근거
Usage usage = response.getMetadata().getUsage();
log.info("prompt={} completion={} total={}",
    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

// ④ 어떤 모델이 답했나 — 폴백이 걸렸는지 확인할 때
String model = response.getMetadata().getModel();
```

> **주의**: length로 끝난 응답을 정상으로 처리하면 잘린 JSON을 파싱하려다 실패하거나, 문장이 끊긴 답이 사용자에게 나간다. 반드시 확인하라.

## 쉽게 말하면 · 프롬프트

*Day 1 · ChatClient*

- 프롬프트는 모델에게 주는 업무 지시서다
- 잘 쓴 지시서 → 역할 · 지시 · 맥락 · 예시 · 형식 다섯 가지
- 애매하게 시키면 애매하게 돌아온다

| 이렇게 생각하면 쉽다 | 실제로는 | 빠뜨리면 |
|---|---|---|
| 누구에게 시키는가 | 시스템 메시지 · 역할·규칙 | 말투와 기준이 매번 바뀐다 |
| 무엇을 해달라 | 지시 · 동사로 분명하게 | 엉뚱한 것을 해 온다 |
| 참고 자료 | 맥락 · 근거 문서·데이터 | 아는 대로 지어낸다 |
| 견본 두어 개 | 예시(Few-shot) | 경계 사례에서 흔들린다 |
| 제출 양식 | 출력 형식 지정 | 매번 모양이 달라 파싱이 깨진다 |

> **지금은 이것만**: 사람에게 일을 시킬 때와 똑같이 쓰면 된다. 신입에게 설명하듯 — 역할 · 할 일 · 참고 자료 · 견본 · 제출 형식. 이 다섯이면 프롬프트는 충분하다.

## 동적 프롬프트 · 파라미터 바인딩

*Day 1 · ChatClient*

- 문자열을 이어 붙이지 말고 자리표시자 + 파라미터로 조립
  - 주입 위험을 줄이고, 프롬프트를 템플릿으로 재사용

```java
String reply = chat.prompt()
    .user(u -> u.text("{topic}를 초보자에게 3문장으로 설명해줘")
                .param("topic", topic))
    .call()
    .content();
```

> **주의**: 사용자 입력을 프롬프트 문자열에 직접 이어 붙이지 말 것. 파라미터로 바인딩하면 관리가 쉽고, 뒤에서 배울 주입 공격 방어에도 유리하다.

## Day 1 정리 · 기본기를 갖췄다

*Day 1 · ChatClient*

- Spring AI → 추상화 + AutoConfiguration · 익숙한 방식으로 LLM을
- 3대 추상화(ChatModel·EmbeddingModel·VectorStore) 위에 모든 기능이 얹힌다
- ChatClient 체인으로 프롬프트 구성 → 호출 → 결과 변환
- 공급자는 설정으로 교체 · 코드는 그대로

> **정리**: 여기까지가 뼈대다. Day 2에서는 이 위에 프롬프트 설계·구조화 출력·RAG를 얹어 "그럴듯한 답"을 "근거 있는 답"으로 끌어올린다.

## 미니 실습 · ChatClient 두 개 (20분)

*Day 1 · ChatClient*

- 빈을 나눠 보면 설정 우선순위가 보인다
- 같은 질문에 다른 성격의 답이 나온다
- ch03.chatclient를 참고한다

| 단계 | 무엇을 한다 | 확인 기준 |
|---|---|---|
| ① | summaryChatClient(온도 0)·ideaChatClient(온도 0.9) 두 빈 생성 | 빈 이름으로 주입된다 |
| ② | 같은 질문을 두 빈으로 3회씩 호출 | 하나는 거의 같고, 하나는 매번 다르다 |
| ③ | application.yml의 모델을 바꿔 보기 | 코드 수정 없이 바뀐다 |
| ④ | 호출 시 `.options()`로 온도를 덮어쓰기 | yml < 빌더 기본값 < 호출별 순으로 이긴다 |
| ⑤ | 응답 메타데이터(usage) 출력 | 프롬프트·완성 토큰이 보인다 |
| ⑥ | dev 프로파일로 모델만 바꿔 실행 | 코드는 그대로, 설정만 바뀐다 |

## 실습 코드 · 말투 바꾸기 (창구 두 개)

*Day 1 · ChatClient*

- 같은 질문에 완전히 다른 말투로 답하게 한다
- 빈이 둘이면 이름으로 골라 받는다
- 온도 차이를 눈으로 확인한다

```java
@Configuration
class ToneConfig {
    @Bean ChatClient 사극체(ChatClient.Builder b) {              // ① 용도별 창구
        return b.defaultSystem("모든 답을 조선시대 사극 말투로 한다. 예: ~하시옵니다")
                .defaultOptions(ChatOptions.builder().temperature(0.2).build()).build();
    }

    @Bean ChatClient 이모지체(ChatClient.Builder b) {
        return b.defaultSystem("모든 답에 어울리는 이모지를 붙여 친근하게 답한다.")
                .defaultOptions(ChatOptions.builder().temperature(0.9).build()).build();
    }
}

@RestController
class ToneLab {
    private final ChatClient 사극체, 이모지체;                    // ② 이름으로 골라 받는다

    ToneLab(ChatClient 사극체, ChatClient 이모지체) {
        this.사극체 = 사극체;  this.이모지체 = 이모지체;
    }

    @GetMapping("/lab4/tone")
    Map<String,String> tone(@RequestParam String q) {
        return Map.of("사극체",   사극체.prompt().user(q).call().content(),
                       "이모지체", 이모지체.prompt().user(q).call().content());
    }
}
// ?q=오늘 회의 30분 늦어요
//  사극체: "송구하옵니다. 회의가 반 시진 늦어지겠사옵니다."
//  이모지체: "앗 죄송해요 🙏 회의 30분만 늦출게요 ⏰"
```

## 실행·테스트 · 창구 두 개

*Day 1 · ChatClient*

- 기동 로그에서 빈 두 개가 떴는지 먼저 확인한다
- 같은 질문에 말투가 다르게 나오면 창구가 갈린 것이다
- 테스트는 어느 빈이 주입됐는지만 본다 · 모델은 부르지 않는다

```bash
# 1) 파일 위치
src/main/java/com/skala/lab4/ToneConfig.java · ToneLab.java
#    → 실행: SpringAI_실습/04_말투바꾸기 폴더를 VS Code 로 열고 F5 (또는 ./gradlew bootRun)
curl 'localhost:8080/lab4/tone?q=오늘 회의 30분 늦어요'

# 2) 기대 결과 — 같은 질문, 다른 말투
{"사극체":"송구하옵니다. 회의가 반 시진 늦어지겠사옵니다.",
 "이모지체":"앗 죄송해요 🙏 회의 30분만 늦출게요 ⏰"}

# 3) 온도 실험 — 각각 3번씩 불러 비교한다
for i in 1 2 3; do curl -s 'localhost:8080/lab4/tone?q=안녕' | jq .사극체; done
#   온도 0.2 → 거의 같은 문장
#   온도 0.9 → 매번 다른 문장
```

```java
// 4) 테스트 — 빈이 제대로 둘 다 뜨는지부터 확인한다
@SpringBootTest
class ToneConfigTest {
    @Autowired ApplicationContext ctx;

    @Test void 창구가_둘_등록된다() {
        assertThat(ctx.getBeansOfType(ChatClient.class)).containsKeys("사극체", "이모지체");
    }
}
```

```
# 안 되면 — 빈 주입 실패: 파라미터 이름이 빈 이름과 다름
#           둘 다 같은 말투: defaultSystem 이 안 걸림(빌더 순서 확인)
```

## 핵심 요약 · 설정과 ChatClient

*Day 1 · ChatClient*

- 이 장의 결론 · 설정은 yml로, 호출은 ChatClient로
- 여기까지가 Spring AI의 기본기다

| 개념 | 한 줄 정리 | 실무 포인트 |
|---|---|---|
| BOM | 모듈 버전을 한 번에 맞춘다 | 버전 올릴 때 이 한 줄만 바꾼다 |
| application.yml | 공급자·모델·옵션을 선언 | API 키는 환경변수로만 |
| ChatClient | ChatModel 위의 Fluent API | Advisor·객체 변환까지 얹혀 있다 |
| 빌더 기본값 | system·options·advisors를 미리 | 용도별로 빈을 나눈다 |
| 파라미터 바인딩 | (변수) + `.param()` | 문자열 연결은 인젝션 표면을 넓힌다 |
| Profile | 환경별로 공급자 전환 | 개발은 소형 모델, 운영은 고성능 모델 |

> **체크**: 추출용과 상담용 ChatClient의 temperature가 같다면 빈을 나눌 때가 된 것이다.
</content>
