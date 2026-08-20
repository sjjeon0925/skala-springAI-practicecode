# 10. Tool Calling · AI Agent · MCP

- 모델이 함수를 부른다
- @Tool로 도구 만들기
- ReAct 멀티스텝 에이전트
- MCP · 도구 표준

## 쉽게 말하면 – Tool Calling

*Day 3 · Tool Calling*

- 모델이 필요한 함수를 지목하면 우리가 실행한다
- 모델은 실행하지 못한다 · 요청만 한다
- 실시간 데이터는 여기서 들어온다

| 이렇게 생각하면 쉽다 | 실제로는 | 그래서 |
|---|---|---|
| 상담원이 조회를 요청 | 모델이 도구 호출을 요청 | 판단은 모델이 한다 |
| 실제 조회는 시스템이 | 우리 코드가 함수를 실행 | 실행 권한은 우리에게 있다 |
| 요청서 양식 | 도구 이름·설명·파라미터 | 설명이 부실하면 안 부른다 |
| 결과를 보고 다시 판단 | 결과를 모델에 되돌려 준다 | 필요하면 또 부른다 |
| 아무나 조회할 수 없다 | 권한 검증은 함수 안에서 | 모델 말을 믿고 열면 안 된다 |

> **지금은 이것만** 판단은 모델, 실행은 우리 코드. 이 한 줄만 잡고 있으면 도구·에이전트·보안 이야기가 전부 자연스럽게 이어진다.

## Tool Calling이란

*Day 3 · Tool Calling*

- 모델은 직접 DB·API를 못 부른다 · 대신 "이 도구를 부르라"고 정한다
- Spring AI가 그 도구(메서드)를 실행해 결과를 다시 모델에 넣는다
- 모델은 실제 데이터에 근거해 최종 답을 만든다

> **참고** 모델이 코드를 실행하는 게 아니다. 무엇을 부를지 판단하고, 실행은 우리 서버(Spring AI)가 한다 · 통제권은 우리에게 있다.

## Tool Calling 실행 흐름

*Day 3 · Tool Calling*

- 질문 → 모델이 도구 호출 결정 → 도구 실행 → 결과로 재요청 → 최종 답
- 이 왕복을 Spring AI가 자동으로 돌린다

모델이 "도구를 부르라" 하면 Spring AI가 `@Tool` 메서드를 실행해 결과를 되먹인다. 호출·인자 파싱·재요청을 프레임워크가 처리한다.

질문 → 모델: 도구 호출 결정 → `@Tool` 실행 → 결과로 재요청 → 최종 답. 이 왕복을 Spring AI가 대신 돌린다 · 인자 파싱·재요청까지.

## @Tool – 도구 정의

*Day 3 · Tool Calling*

- 평범한 메서드에 `@Tool`을 붙이면 도구가 된다
- 설명(description)이 곧 모델에게 주는 사용 설명서

```java
@Component
class WeatherTools {
    @Tool(description = "도시의 현재 날씨를 조회한다")
    String currentWeather(
            @ToolParam(description = "도시 이름") String city) {
        return weatherApi.fetch(city);   // 실제 API 호출
    }
}
```

## Tool 스키마 – 모델은 무엇을 보나

*Day 3 · Tool Calling*

- 모델에게 전달되는 것은 이름 · 설명 · 파라미터 스키마 셋뿐이다
- 메서드 본문은 모델이 절대 보지 못한다 · 설명이 전부다
- 설명이 곧 인터페이스 · 대충 쓰면 엉뚱하게 부른다

```java
@Tool(description = "주문번호로 배송 상태와 예상 도착일을 조회한다. 본인 주문만 조회된다.")
public String orderStatus(
        @ToolParam(description = "주문번호(숫자 5자리)") String orderId,
        ToolContext context) { ... }

// ↓ 모델에게 실제로 전달되는 것
// {
//   "name": "orderStatus",
//   "description": "주문번호로 배송 상태와 예상 도착일을 조회한다. ...",
//   "parameters": {
//     "type": "object",
//     "properties": {
//       "orderId": {"type":"string","description":"주문번호(숫자 5자리)"}
//     },
//     "required": ["orderId"]
//   }
// }
// ※ ToolContext는 스키마에 포함되지 않는다 — 모델이 볼 수 없다
```

## Tool 등록 – ChatClient에 연결

*Day 3 · Tool Calling*

- `.tools()`로 도구 객체를 ChatClient에 넘긴다
- 모델이 필요하다고 판단하면 자동으로 호출된다

```java
String answer = chat.prompt()
        .user("서울 지금 날씨 어때?")
        .tools(weatherTools)          // 도구 등록
        .call()
        .content();
// 모델이 currentWeather("서울")을 부르게 판단 (예시)
```

## ToolCallback – 저수준 도구 등록

*Day 3 · Tool Calling*

- `@Tool`이 안 맞는 경우 · 런타임에 도구 목록이 결정되는 상황
- MethodToolCallbackProvider로 여러 객체를 한 번에, 빈으로 등록
- FunctionToolCallback은 람다·함수도 도구로 만든다

```java
@Configuration
class ToolConfig {
    // ① 어노테이션 도구들을 모아 전역 등록 — 모든 ChatClient가 쓴다
    @Bean
    ToolCallbackProvider appTools(OrderTools order, TicketTools ticket) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(order, ticket).build();
    }

    // ② 함수 하나를 도구로 — 입력 타입만 알려 주면 된다
    @Bean
    ToolCallback exchangeRate(RateClient client) {
        return FunctionToolCallback
                .builder("exchangeRate", (RateReq r) -> client.rate(r))
                .description("두 통화 사이의 현재 환율을 조회한다")
                .inputType(RateReq.class).build();
    }
}
record RateReq(String from, String to) {}
```

## 실전 도구 – DB 조회

*Day 3 · Tool Calling*

- 도구 안에서 기존 Repository·서비스를 그대로 호출한다
- AI가 우리 시스템의 실시간 데이터에 근거해 답하게 된다

```java
@Component
class OrderTools {
    private final OrderRepository orders;

    @Tool(description = "주문번호로 주문 상태를 조회한다")
    OrderStatus status(String orderNo) {
        return orders.findByNo(orderNo)
                .map(Order::getStatus).orElseThrow();
    }
}
```

## 복수 Tool과 에러 처리

*Day 3 · Tool Calling*

- 여러 도구를 등록하면 모델이 상황에 맞게 골라 부른다
- 도구 실행이 실패하면 명확한 메시지·예외로 모델에 알린다
- 모델이 실패를 인지하고 다른 방법을 시도하거나 사용자에게 안내

> **주의** 도구는 외부 세계와 닿는 접점이다. 권한 검사·입력 검증·감사 로깅을 도구 안에 두어라 · 모델이 시켰다고 무조건 실행하면 안 된다.

## Tool 실행 제어 – 반환·예외·컨텍스트

*Day 3 · Tool Calling*

- 도구 예외를 그대로 던지면 대화 전체가 실패한다 · 메시지로 바꿔 돌려주자
- ToolContext로 모델에게 노출하지 않을 값(사용자 ID 등)을 전달
- 반환값은 모델이 읽을 문장 · JSON 덩어리보다 요약된 문장이 낫다

```java
@Component
class OrderTools {
    @Tool(description = "주문번호로 배송 상태를 조회한다")
    String orderStatus(@ToolParam(description = "주문번호") String orderId,
                        ToolContext ctx) {                 // 모델에 노출 안 됨
        String userId = (String) ctx.getContext().get("userId");
        try {
            Order o = orderService.findOwned(orderId, userId);   // 소유자 검증
            return "주문 %s: %s, 예상 도착 %s".formatted(
                    o.id(), o.status(), o.eta());          // 모델이 읽기 좋은 문장
        } catch (NotFoundException e) {
            return "해당 주문을 찾을 수 없습니다.";          // 예외 대신 메시지
        }
    }
}

String answer = chat.prompt().user(q).tools(orderTools)
        .toolContext(Map.of("userId", currentUserId))     // 안전한 경로로 주입
        .call().content();
```

> **주의** 사용자 ID를 프롬프트에 적지 마라. 모델이 바꿔 부를 수 있다. ToolContext는 모델을 거치지 않고 도구에 직접 전달되는 통로다.

## 병렬 Tool 호출

*Day 3 · Tool Calling*

- 모델은 한 번에 여러 도구를 동시에 부르겠다고 응답할 수 있다
- "서울과 부산 날씨 알려줘" → currentWeather 두 번을 한 번에
- 도구가 서로 독립적이고 부작용이 없어야 안전하다

```java
// 사용자: "서울이랑 부산 날씨 둘 다 알려줘"
//
// 모델 응답(1차) — tool_calls 두 건이 한 번에 온다
//   [ {name: currentWeather, args: {city: "서울"}},
//     {name: currentWeather, args: {city: "부산"}} ]
//
// Spring AI가 두 호출을 실행하고 결과를 모아 2차 요청을 보낸다.
// 우리 코드는 그냥 @Tool 메서드일 뿐 — 별도 처리가 필요 없다.
@Tool(description = "지정한 도시의 현재 날씨를 조회한다")
public String currentWeather(@ToolParam(description = "도시 이름") String city) {
    return weatherApi.fetch(city);      // 이 메서드가 두 번 호출된다
}
```

> **주의** 도구가 상태를 바꾸면 병렬 호출이 위험하다. 조회 도구는 안전하지만, 쓰기 도구는 같은 자원에 동시에 닿을 수 있다 · 멱등성을 확보하거나 순차 실행을 강제하라.

## Tool 실패와 재시도

*Day 3 · Tool Calling*

- 도구가 던진 예외는 대화 전체를 실패시킨다 · 기본 동작이다
- 복구 가능한 실패는 메시지로 돌려주면 모델이 다음 수를 둔다
- 모델에게 재시도를 맡기지 마라 · 같은 도구를 무한히 부를 수 있다

```java
@Tool(description = "주문 상태를 조회한다")
String orderStatus(String orderId, ToolContext ctx) {
    try {
        return orders.findOwned(orderId, userOf(ctx))
                .map(this::describe)
                .orElse("해당 주문번호를 찾을 수 없습니다. 번호를 다시 확인해 주세요.");
    } catch (TimeoutException e) {          // 일시적 — 우리가 재시도한다
        log.warn("주문 API 지연 — 재시도", e);
        return retryOnce(orderId, ctx);
    } catch (Exception e) {                 // 복구 불가 — 상황만 알린다
        log.error("주문 조회 실패 orderId={}", orderId, e);
        return "지금은 주문 정보를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.";
    }
}
```

> **주의** "다시 시도해 보세요"를 반환하면 모델이 곧바로 같은 도구를 또 부른다. 재시도는 우리 코드 안에서 횟수를 정해 하고, 모델에게는 결과만 알려라.

## 쉽게 말하면 – AI 에이전트

*Day 3 · Agent와 MCP*

- 한 번에 못 끝나는 일을 여러 번 나눠 처리한다
- 생각하고 → 해 보고 → 결과를 보고 다시 생각한다
- 반드시 멈추는 조건을 정해 둔다

| 이렇게 생각하면 쉽다 | 실제로는 | 주의 |
|---|---|---|
| 신입이 혼자 일 처리하기 | 에이전트 · 도구를 쓰는 반복 | 잘하면 편하고 못하면 사고 |
| 해 보고 결과를 본다 | 도구 호출 → 결과 확인 | 결과가 애매하면 계속 시도한다 |
| 다시 판단한다 | ReAct · 생각과 행동 반복 | 몇 번이고 돌 수 있다 |
| "세 번까지만 시도" | 루프 상한 · 재시도 제한 | 없으면 밤새 돈다 |
| 큰 결정은 보고하고 한다 | 승인 게이트(사람 확인) | 환불·삭제는 사람이 확정 |

> **지금은 이것만** 에이전트는 똑똑한 존재가 아니라 반복하는 구조다. 그래서 잘 도는 것보다 언제 멈추는지를 먼저 정해야 한다 · 상한 없는 에이전트는 만들면 안 된다.

## AI Agent – ReAct 패턴

*Day 3 · Agent와 MCP*

- Tool Calling을 여러 스텝 이어 복잡한 작업을 자동화
- 생각(Reason) → 행동(Act) → 관찰(Observe)을 목표까지 반복

한 번에 답하지 않고 생각·도구 실행·관찰을 반복하며 문제를 좁힌다. Tool Calling이 한 번의 호출이라면, ReAct는 그것을 여러 스텝 잇는다.

생각(Reason) → 행동(Act) → 관찰(Observe) — 목표에 닿을 때까지 반복 · Tool Calling을 여러 스텝 잇는다.

## 쉽게 말하면 – MCP

*Day 3 · Agent와 MCP*

- 도구를 붙이는 표준 규격이다
- USB처럼 규격만 맞으면 꽂힌다
- 처음에는 "이런 게 있다" 정도면 충분하다

| 이렇게 생각하면 쉽다 | 실제로는 | 이득·주의 |
|---|---|---|
| 기기마다 다른 충전기 | 도구마다 다른 연동 코드 | 붙일 때마다 새로 만든다 |
| USB-C 하나로 통일 | MCP · 도구 연결 표준 | 규격만 맞으면 그대로 꽂힌다 |
| 주변기기를 꽂아 쓴다 | MCP 클라이언트 | 파일·DB·사내 시스템 연결 |
| 우리 기기를 남이 쓰게 한다 | MCP 서버 | 우리 도구를 표준으로 공개 |
| 아무 기기나 꽂지 않는다 | 인증·공개 범위 통제 | 원격 실행 통로가 될 수 있다 |

> **지금은 이것만** MCP는 도구를 붙이는 USB 규격 같은 것이다. 지금은 이 정도만 알아도 되고, 실제로 붙이는 일은 도구가 여러 개로 늘어난 다음에 해도 늦지 않다.

## MCP – 도구를 표준 규약으로

*Day 3 · Agent와 MCP*

- Model Context Protocol · 도구·자원을 공통 프로토콜로 노출
- 한 번 MCP 서버로 만들면 여러 AI 앱이 공유한다

앱마다 도구를 새로 붙이는 대신 표준 프로토콜(MCP)로 노출한다. Spring AI는 MCP 클라이언트·서버를 모두 지원해 도구 생태계에 연결된다.

- AI 앱 → MCP 클라이언트로 붙는다
- MCP 서버 → 도구·자원을 표준 규약으로 노출
- 도구·자원, 한 번 만들면 여러 앱이 공유

Spring AI는 클라이언트·서버를 모두 지원한다.

## MCP 클라이언트 – 외부 도구에 연결

*Day 3 · Agent와 MCP*

- MCP 서버가 제공하는 도구를 내 앱의 도구처럼 쓴다
- 스타터 + application.yml 연결 정보 → ToolCallbackProvider 자동 구성
- 파일시스템·DB·사내 시스템을 표준 규약으로 붙인다

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: helpdesk-client
        stdio:                          # 로컬 프로세스로 띄우는 MCP 서버
          connections:
            filesystem:
              command: npx
              args: ["-y", "@modelcontextprotocol/server-filesystem", "/data"]
        sse:                            # 원격 MCP 서버
          connections:
            internal: { url: "http://mcp-internal:8080" }
```

```java
@Bean
ChatClient mcpChatClient(ChatClient.Builder b,
                          SyncMcpToolCallbackProvider mcpTools) {
    return b.defaultToolCallbacks(mcpTools).build();   // 자동 구성된 도구 주입
}
```

## MCP 서버 – 우리 도구를 공개하기

*Day 3 · Agent와 MCP*

- 우리 시스템의 기능을 다른 AI 앱도 쓸 수 있게 표준으로 노출
- `@Tool` 메서드를 그대로 MCP 도구로 공개한다 · 코드 재사용
- 공개 범위·인증은 일반 웹 보안과 동일하게 통제한다

```java
// build.gradle
//   implementation "org.springframework.ai:spring-ai-starter-mcp-server-webmvc"

@Configuration
class McpServerConfig {
    @Bean
    ToolCallbackProvider helpdeskTools(TicketTools tickets, KbTools kb) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tickets, kb)          // @Tool 메서드가 곧 MCP 도구
                .build();
    }
}

// application.yml
//   spring.ai.mcp.server.name: helpdesk-mcp
//   spring.ai.mcp.server.version: 1.0.0
//   → /mcp 엔드포인트로 도구 목록·호출이 노출된다
```

## 정리 – 말하는 AI에서 일하는 AI로

*Day 3 · Agent와 MCP*

- `@Tool` 메서드 하나로 모델을 우리 시스템과 잇는다
- RAG(문서)와 Tool(실시간 데이터)은 상호 보완
- Tool을 여러 스텝 이으면 에이전트(ReAct), 표준화하면 MCP

> **정리** 이제 AI가 행동한다. 남은 건 이 힘을 안전하고 관리 가능하게 만드는 것 · 다음은 Advisor·메모리·관찰 가능성으로 운영 품질을 올린다.

## MCP 프로토콜 구조

*Day 3 · Agent와 MCP*

- MCP는 AI 앱과 도구 제공자 사이의 표준 규약이다
- "도구 목록을 알려 줘"와 "이 도구를 실행해 줘" 두 가지가 핵심
- 전송 방식만 다를 뿐 규약은 동일 · stdio(로컬) · HTTP/SSE(원격)

| 개념 | 무엇인가 | 우리 쪽 역할 |
|---|---|---|
| Host | AI 앱(우리 Spring Boot 앱) | MCP 클라이언트를 품는다 |
| Client | 서버 하나와 연결을 유지 | 스타터가 자동 구성 |
| Server | 도구·리소스를 제공하는 쪽 | 우리가 서버가 될 수도 있다 |
| Tools | 실행 가능한 기능 | `@Tool`이 그대로 공개된다 |
| Resources | 읽을 수 있는 데이터 | 파일 · DB 레코드 등 |
| Prompts | 재사용 가능한 프롬프트 템플릿 | 서버가 제공하는 정형 질의 |
| transport | stdio · HTTP/SSE | 로컬 프로세스냐 원격이냐 |

## 미니 실습 – 도구와 ReAct

*Day 3 · Agent와 MCP*

- 도구가 언제 불리는지를 눈으로 본다
- 설명을 일부러 나쁘게 써 보고 비교한다
- ch09/tools를 참고한다

| 단계 | 무엇을 한다 | 확인 기준 |
|---|---|---|
| ① | `@Tool`로 주문 조회 도구 만들기 | 설명에 "언제 쓰는지"를 쓴다 |
| ② | "안녕하세요"를 물어본다 | 도구가 불리지 않는다 |
| ③ | "12345 어디야"를 물어본다 | 도구가 불린다 · 로그로 확인 |
| ④ | 설명을 한 단어로 줄이고 ③ 반복 | 호출률이 떨어진다 · 설명이 곧 스펙 |
| ⑤ | 날씨 도구를 추가하고 복합 질문 | 둘 다 불리는지 · 순서는 어떤지 |
| ⑥ | 도구에서 예외를 던져 보기 | 모델이 사용자에게 어떻게 전달하는가 |

## 실습 코드 – 점심 추천 도구 (도구 두 개)

*Day 3 · Agent와 MCP*

- "더운데 점심 뭐 먹지" → 도구 두 개가 불린다
- 설명을 줄이면 호출이 사라진다 · 직접 확인한다
- 인사에는 도구를 안 부르는지도 본다

```java
@Component
class LunchTools {
    @Tool(description = """
            오늘의 점심 메뉴를 추천한다.
            '점심 뭐 먹지', '메뉴 추천해줘', '배고파' 같은 말에 사용한다.
            """)                                    // ← 모델이 보는 것은 이 설명뿐이다
    public String 점심추천(
            @ToolParam(description = "지금 기분이나 날씨. 예: 피곤, 더움") String 기분) {
        return switch (기분) {
            case "피곤" -> "국밥 (뜨끈하게 한 그릇)";
            case "더움" -> "냉면 (시원하게)";
            default     -> "김치찌개 (무난하게)";
        };
    }

    @Tool(description = "지금 서울 날씨를 알려 준다.")
    public String 날씨() { return "맑음, 28도"; }
}

// 등록 — 도구를 붙여서 물어본다
String 답 = chat.prompt().user(질문).tools(lunchTools).call().content();
// "안녕하세요"          → 도구 호출 없음 (그냥 인사한다)
// "더운데 점심 뭐 먹지" → 날씨() 부르고 → 점심추천("더움") 부르고
//                        → "지금 28도로 더우니 냉면 어떠세요?"
// 실험: 설명을 "점심 추천" 네 글자로 줄이면 → 갑자기 도구를 안 부른다
```

## 실행·테스트 – 점심 추천 도구

*Day 3 · Agent와 MCP*

- 질문 세 개로 도구가 언제 불리고 언제 안 불리는지 본다
- 도구가 안 불리면 설명문(description)부터 다시 쓴다
- 테스트는 도구 메서드를 직접 부른다 · 모델 없이 된다

```bash
# 1) 파일 위치
src/main/java/com/skala/lab10/LunchTools.java · LunchController.java
#    → 실행: SpringAI_실습/10_점심추천도구 폴더를 VS Code로 열고 F5 (또는 ./gradlew bootRun)

# 2) 세 가지 질문으로 호출 — 도구가 언제 불리는지 본다
curl 'localhost:8080/lab10/ask?q=안녕하세요'          # 도구 호출 없음
curl 'localhost:8080/lab10/ask?q=점심 뭐 먹지'        # 점심추천 1개
curl 'localhost:8080/lab10/ask?q=더운데 점심 뭐 먹지' # 날씨 + 점심추천 2개

# 3) 로그로 확인 (application.yml에서 DEBUG로 올린다)
logging.level.org.springframework.ai.tool: DEBUG
#   Tool call: 날씨()  →  "맑음, 28도"
#   Tool call: 점심추천(기분=더움)  →  "냉면 (시원하게)"

# 4) 설명 줄이기 실험 — 이 장의 하이라이트
#   @Tool(description = "점심 추천")로 줄인 뒤 같은 질문을 다시 던진다
#   → 도구가 잘 안 불린다. 설명을 되돌리면 다시 불린다.

# 5) 테스트 — 도구는 그냥 메서드다. 모델 없이 직접 부른다.
@Test void 기분에_따라_메뉴가_달라진다() {
    assertThat(tools.점심추천("피곤")).contains("국밥");
    assertThat(tools.점심추천("더움")).contains("냉면");
}
# 안 되면 — 호출 안 됨: 설명 부실 · 인자 이상: @ToolParam 설명에 예시 추가
```

## 핵심 요약 – Tool Calling과 Agent

*Day 3 · Agent와 MCP*

- 이 장의 결론 · 말하는 AI에서 일하는 AI로
- 모델은 함수를 실행하지 않는다 · 무엇을 부를지 알려 줄 뿐이다

| 개념 | 한 줄 정리 | 실무 포인트 |
|---|---|---|
| `@Tool` | 평범한 메서드가 도구가 된다 | description이 곧 사용 설명서 |
| `@ToolParam` | 인자의 의미를 설명한다 | 모호하면 엉뚱한 값으로 부른다 |
| ToolContext | 모델에 노출하지 않는 값 전달 | 사용자 ID는 프롬프트에 적지 않는다 |
| 실행 주체 | Spring AI가 우리 코드를 부른다 | 권한·검증은 전적으로 우리 책임 |
| 반환값 | 모델이 읽을 문장으로 | 엔티티 통째로는 토큰만 먹는다 |
| ReAct | 생각 → 도구 → 관찰의 반복 | 반복 상한이 없으면 비용 사고 |
| MCP | 도구를 붙이는 방식의 표준 | 서버가 늘어도 코드는 그대로 |

> **체크** 도구 안에 소유자 검증이 없다면 "주문번호 아무거나 대보기"로 남의 데이터가 조회된다.
