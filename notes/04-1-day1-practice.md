# Day 1 실습 — 계층 위에 AI 얹기

- 환경 확인부터 첫 응답까지
- ChatClient 빈과 Service 계층
- Swagger로 검증하고 실패까지 다루기
- 90분 · 혼자 또는 2인 1조

## 오늘 만들 것 — 주문 요약 API

- 오늘 배운 것을 하나의 엔드포인트로 합친다
- 막히면 ch03/chatclient 예제를 연다

| 구분 | 무엇을 | 확인 방법 |
|---|---|---|
| 목표 | 주문 하나를 AI가 한 문장으로 요약 | GET /lab1/orders/{id}/summary |
| 계층 | Controller → Service → (Repository, ChatClient) | 컨트롤러에 ChatClient가 없으면 통과 |
| 설정 | AiConfig에 ChatClient 빈 하나 | 시스템 프롬프트 · 온도 0 · 토큰 상한 |
| 검증 | Swagger "Try it out" | 200 → 한 문장 요약 |
| 실패 | 키 없음 · 429 · 타임아웃 | 폴백 문구가 나온다 |
| 산출물 | day1 패키지 · 파일 5개 | 완료 기준 8개 |
| 시간 | 90분 | 세팅 10 · 구현 60 · 정리 20 |

## 시작 전 점검 — 여기서 막히면 진도가 안 나간다

- 여섯 줄을 먼저 통과하고 시작한다
- 하나라도 막히면 3장 트러블슈팅으로 돌아간다
- 옆 사람과 서로 확인해 주면 빠르다

| 확인할 것 | 명령 · 화면 | 통과 기준 |
|---|---|---|
| JDK | `java --version` | 21이 나온다 |
| 프로젝트 | VS Code로 실습 폴더 열기 | 권장 확장 설치 알림 → 설치 |
| 실행 | F5 또는 `./gradlew bootRun` | 기동 로그에 포트 8080 |
| 키 | `echo $OPENAI_API_KEY` | sk-로 시작 (없으면 실습을 시작할 수 없다) |
| 계층 예제 | `/ch02/orders/12345?userId=user1` | 200 → JSON (키 없이도 동작) |
| 문서 | `/swagger-ui.html` | 그룹 세 개가 보인다 |

> **체크** 여기서 하나라도 막히면 실습을 시작하지 말고 먼저 해결한다. 환경 문제를 안고 시작하면 나중에 코드 문제와 섞여 원인을 가릴 수 없게 된다 → 3장 트러블슈팅 표로 돌아간다.

## Step 1 — ChatClient 빈 만들기

- 용도별로 빈을 나눈다 — 요약 전용
- 온도와 토큰 상한을 빈에서 못 박는다
- 시스템 프롬프트에 거절 규칙을 함께 넣는다

```java
// day1/config/Lab1AiConfig.java
@Configuration
class Lab1AiConfig {
    @Bean                                    // 용도별로 나눈다 — 요약 전용 클라이언트
    ChatClient summaryChatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("""
                너는 이커머스 주문 상담 도우미다.
                주어진 주문 정보만 사용해 한국어 한 문장으로 요약한다.
                추측하지 않는다. 정보가 부족하면 "정보가 부족합니다"라고 답한다.
                """)
            .defaultOptions(ChatOptions.builder()
                .temperature(0.0)    // 요약은 매번 같아야 한다
                .maxTokens(120)      // 비용 상한 — 길게 쓸 이유가 없다
                .build())
            .build();
    }
}
```

> **주의** 온도와 최대 토큰을 빈에서 못 박아 둔다. 호출부마다 정하게 두면 누군가는 기본값(0.7)으로 부르고, 그날부터 요약이 매번 달라진다.

## Step 2 — Service: 업무 흐름은 여기

- 1장에서 만든 계층을 그대로 쓴다
- 주문을 못 찾으면 모델을 부르지 않는다
- 서비스는 무엇을 하는가만 읽혀야 한다

```java
// day1/service/OrderSummaryService.java
@Service
@Transactional(readOnly = true)
public class OrderSummaryService {
    private final OrderRepository orders;      // 1장에서 만든 계층을 그대로
    private final ChatClient summaryChat;

    public OrderSummaryService(OrderRepository orders, ChatClient summaryChatClient) {
        this.orders = orders;  this.summaryChat = summaryChatClient;
    }

    public SummaryResponse summarize(String orderId, String userId) {
        Order order = orders.findByIdAndOwnerId(orderId, userId)   // 권한은 쿼리 안에
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        String summary = summaryChat.prompt()
            .user(u -> u.text("주문번호 {id} · 상품 {item} · 상태 {status} · 도착예정 {eta}"
                    + "\n위 정보를 한 문장으로 요약해 줘.")
                .param("id", order.getId()).param("item", order.getItem())
                .param("status", order.getStatus().label()).param("eta", order.getEta()))
            .call().content();

        return new SummaryResponse(order.getId(), summary);
    }
}
```

## Step 3 — Controller와 문서

- 컨트롤러는 AI를 모른다 — 오늘의 합격 기준
- 문서 애노테이션을 함께 단다
- 응답 DTO에 예시를 넣어 둔다

```java
// day1/web/OrderSummaryController.java
@RestController
@RequestMapping("/lab1/orders")
@Tag(name = "Day1 실습 · 주문 요약")
public class OrderSummaryController {
    private final OrderSummaryService service;   // ← ChatClient는 여기 없다

    @GetMapping("/{orderId}/summary")
    @Operation(summary = "주문 한 문장 요약",
        description = "본인 주문만 요약된다. 모델을 호출하므로 비용이 발생한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "요약 성공"),
        @ApiResponse(responseCode = "404", description = "없는 주문이거나 남의 주문")})
    public SummaryResponse summary(
        @Parameter(description = "주문번호", example = "12345") @PathVariable String orderId,
        @Parameter(description = "조회 주체", example = "user1") @RequestParam String userId) {
        return service.summarize(orderId, userId);
    }
}

record SummaryResponse(@Schema(example = "12345") String orderId,
    @Schema(example = "무선 이어폰이 배송 중이며 7월 30일 도착 예정입니다.")
    String summary) {}
```

> **체크** 이 파일에 ChatClient가 import 되어 있으면 되돌린다. 컨트롤러가 AI를 모르는 상태로 남아 있어야, 나중에 모델을 바꿔도 웹 계층은 그대로다.

## Step 4 — Swagger로 검증

- 만들었으면 여섯 줄을 눌러 본다
- 같은 입력에 같은 답이 나오는지 확인한다
- 응답 시간과 토큰도 함께 본다

| 눌러 볼 것 | 입력 | 기대 결과 |
|---|---|---|
| 정상 | 12345 / user1 | 200 → 한 문장 요약 |
| 남의 주문 | 99999 / user1 | 404 (99999는 user2 소유) |
| 없는 주문 | 00000 / user1 | 404 — 위와 같은 응답 (존재 여부를 알리지 않는다) |
| 재현성 | 같은 입력으로 3회 | 문장이 거의 같다 (온도 0) |
| 응답 시간 | duration 확인 | 1~3초 — 대부분 모델 호출 구간 |
| 토큰 | `/actuator/metrics/ai.tokens` | 호출할 때마다 증가한다 |

## Step 5 — 실패를 다룬다

- 예외를 응답으로 바꾸는 자리는 한 곳이다
- AI가 실패해도 화면은 살린다
- 상세는 로그에, 사용자에게는 추적 ID만

```java
// ① 예외 → 응답 변환은 한 곳에서 (1장)
@RestControllerAdvice
class Lab1ExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(OrderNotFoundException e) {
        return ResponseEntity.status(404).body(new ErrorResponse("주문을 찾을 수 없습니다.", null));
    }

    @ExceptionHandler(Exception.class)              // 모델 오류·타임아웃 포함
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] 요약 실패", traceId, e);      // 상세는 로그에만
        return ResponseEntity.status(503).body(new ErrorResponse(
            "요약을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.", traceId));
    }
}

// ② 더 나은 방법 — AI가 죽어도 주문 정보는 보여 준다
String summary;
try   { summary = callModel(order); }
catch (Exception e) { summary = order.getItem() + " · " + order.getStatus().label(); }
```

> **주의** AI 기능이 실패했다고 화면 전체가 실패하면 안 된다. 요약은 부가 정보다 — 없으면 없는 대로 주문 정보는 보여 준다. 이 판단이 데모와 실서비스를 가른다.

## 실험 — 값을 바꿔 보며 감 잡기

- 여섯 줄이 내일(5장)의 예고편이다
- 오늘 손으로 만져 본 감각이 내일 개념이 된다
- 한 번에 하나만 바꾼다

| 바꿔 볼 것 | 어떻게 | 무엇을 관찰하나 |
|---|---|---|
| 온도 | 0 → 0.9로 바꿔 5회 호출 | 문장이 매번 달라진다 |
| maxTokens | 120 → 20 | 문장이 중간에서 끊긴다 |
| 시스템 프롬프트 | "한 문장" → "세 문장으로" | 지시가 얼마나 잘 지켜지나 |
| 정보 누락 | 프롬프트에서 eta를 빼 본다 | "정보가 부족합니다"가 나오나 (거절 검증) |
| 모델 교체 | gpt-4o-mini → gpt-4o | 품질과 지연의 차이 |
| 스트리밍 | `.stream()`으로 호출 | 첫 글자까지 걸리는 시간 |

## 자주 막히는 지점 — Day 1 실습

- 첫날은 환경 문제가 대부분이다
- 증상 → 원인이 거의 일대일이다
- 3분 넘게 막히면 손을 든다

| 증상 | 원인 | 해결 |
|---|---|---|
| 기동은 되는데 호출하면 502 | 환경변수 미설정 (키는 부를 때 검사한다) | `export OPENAI_API_KEY=...` 후 재시작 (VS Code는 launch.json) |
| 401 Unauthorized | 키 오류 · 크레딧 없음 | 공급자 콘솔에서 키와 결제 확인 |
| 429 Too Many Requests | 다 같이 호출해서 레이트 리밋 | 잠시 후 재시도 (실습에서 흔하다) |
| 빈 주입 실패 | 파라미터 이름과 빈 이름 불일치 | summaryChatClient로 이름을 맞춘다 |
| 요약이 영어로 나온다 | 시스템 프롬프트에 언어 미지정 | "한국어로"를 명시 |
| 한글이 깨진다 | 인코딩 불일치 | settings.json `files.encoding: utf8` |
| 항상 404 | userId 불일치 | 12345·12346·12347은 user1 소유 |

> **체크** 표에서 안 잡히면 로그의 `Caused by` 마지막 줄을 본다. 스택트레이스 맨 위가 아니라 그 줄이 진짜 원인인 경우가 대부분이다 — 이 습관 하나가 디버깅 시간을 절반으로 줄인다.

## 완료 기준 — Day 1, 그리고 내일

- 8개 중 6개 이상이면 오늘 목표 달성이다
- 3 · 8번이 오늘의 진짜 학습 지점이다
- 오늘 만든 것은 그릇이다

| # | 확인 항목 | 통과 기준 |
|---|---|---|
| 1 | 엔드포인트 동작 | `/lab1/orders/12345/summary?userId=user1`이 200 |
| 2 | 권한 격리 | 99999 요청이 404 |
| 3 | 계층 분리 | 컨트롤러에 ChatClient가 없다 |
| 4 | 빈 구성 | ChatClient 빈이 config에 하나 |
| 5 | 옵션 고정 | 같은 입력에 거의 같은 답 |
| 6 | 문서화 | Swagger에 설명·예시·404가 보인다 |
| 7 | 실패 처리 | 모델 오류 시 503 → traceId |
| 8 | 폴백 | AI가 실패해도 주문 정보는 나간다 |

## Day 1 되짚기 — 어제 한 일 (Day 2 · 시작하며)

- 어제의 결론은 하나였다 — AI를 부르는 일은 특별하지 않다
- 계층을 먼저 세우고, AI는 그 위에 얹었다 — 순서가 반대면 코드가 엉킨다
- 오늘은 같은 호출로 더 좋은 답을 받는 법을 배운다

| 어제 배운 것 | 한 줄 요약 | 오늘 어디에 쓰나 |
|---|---|---|
| 계층 구조 | 컨트롤러는 받고, 서비스가 판단하고, 리포지토리가 저장한다 | AI 호출도 서비스 안에 둔다 |
| 빈과 주입 | new 대신 주입받아 쓴다 | ChatClient도 빈으로 주입받는다 |
| 3대 추상화 | ChatClient · Advisor · VectorStore 셋이 뼈대다 | 오늘 Advisor를 본격적으로 쓴다 |
| ChatClient | prompt() → user() → call() → content() 한 줄 흐름 | 프롬프트와 옵션이 이 흐름에 붙는다 |
| Swagger 검증 | 브라우저에서 바로 호출해 눈으로 확인한다 | 오늘 실습도 같은 방식으로 검증한다 |
| API 키 관리 | 코드에 적지 않고 환경변수로 넣는다 | 오늘도 어제 넣어 둔 키를 그대로 쓴다 |

## Day 1 체크리스트 — 여기까지 됐나 (Day 2 · 시작하며)

- 아래 여섯 개가 되면 오늘 내용을 따라오는 데 무리가 없다
- 하나라도 막히면 쉬는 시간에 반드시 풀고 시작하자
- 완벽하지 않아도 된다 — 오늘 반복하면서 자연스럽게 익는다

| 확인할 것 | 어떻게 확인하나 | 안 되면 이렇게 |
|---|---|---|
| 프로젝트 실행 | VS Code에서 F5를 누르면 앱이 뜬다 | JDK 버전과 Gradle 동기화부터 확인 |
| API 키 | 앱 로그에 키 관련 오류가 없다 | 터미널에서 `echo $OPENAI_API_KEY` 확인 |
| 첫 응답 | 테스트 엔드포인트에 모델 답이 돌아온다 | 키 → 네트워크 → 모델 이름 순으로 점검 |
| Swagger | `/swagger-ui.html`이 브라우저에서 열린다 | springdoc 의존성 누락이 대부분이다 |
| 계층 분리 | 컨트롤러에 AI 호출 코드가 남아 있지 않다 | 서비스로 옮기기만 하면 된다 |
| 미니 실습 | 1 · 2 · 3 · 4장 미니 실습을 한 번씩 해 봤다 | 하나만 골라 쉬는 시간에 다시 해 본다 |

> **체크** 오늘 실습은 어제 만든 프로젝트 위에 그대로 이어서 한다. 지금 막힌 것을 안고 시작하면 오늘 문제인지 어제 문제인지 구분이 안 된다.

## 오늘의 지도 — Day 2 미리보기 (Day 2 · 시작하며)

- 어제가 부르는 법 이었다면, 오늘은 잘 묻고 잘 받는 법이다
- 오전은 프롬프트와 응답 제어, 오후는 RAG다
- 오늘의 목표는 모델을 바꾸지 않고 답을 좋게 만드는 것

| 시간 | 무엇을 배우나 | 끝나면 할 수 있는 것 |
|---|---|---|
| 10:00~11:00 · 5장 | 프롬프트 템플릿 · 옵션 · 스트리밍 | 같은 모델로 답 품질을 끌어올린다 |
| 11:00~12:00 · 6장 | 구조화 출력 · 멀티모달 · 임베딩 | 응답을 자바 객체로 바로 받는다 |
| 13:10~14:00 · 7장 | LLM 활용 심화 | 테스트 · 라우팅으로 호출을 다듬는다 |
| 14:00~15:00 · 8장 | RAG — 우리 문서를 근거로 | 우리 문서를 근거로 답하게 만든다 |
| 15:00~16:00 · 9장 | RAG 심화 — 고급 패턴 | 검색이 아쉬울 때 어디를 손볼지 안다 |
| 16:00~ 실습 | 사내 문서 Q&A 만들기 (100분) | 인제스트 → 검색 → 답변 → 측정까지 완주 |
