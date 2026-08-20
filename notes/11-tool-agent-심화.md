# 11. Tool·Agent 심화

- 감사 로깅 (AOP)
- 권한 제어 (Security)
- Tool 설계 원칙
- 최적화 전략

## 감사 로깅 — 모든 호출을 기록

*심화 · Tool 안전*

- 도구가 무엇을·언제·어떤 인자로 실행됐는지 남긴다
- AOP로 가로채면 각 도구 코드를 건드리지 않고 일관되게

`@Around` Aspect가 모든 도구 호출을 가로채 인자·결과·시각을 기록한다. 각 `@Tool` 코드를 손대지 않고도 일관된 감사 추적이 생긴다 — 규제 대응의 기본.

```
도구 호출 → @Around Aspect → 인자·결과·시각 기록 → 감사 로그
```

각 도구 코드를 손대지 않고 일관된 추적이 생긴다.

## 기본 코드 틀 — 감사 Aspect

*심화 · Tool 안전*

```java
@Aspect
@Component
class ToolAuditAspect {
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String tool = pjp.getSignature().getName();
        Object result = pjp.proceed(); // 실제 실행
        log.info("tool={} args={} user={}", tool,
            pjp.getArgs(), currentUser());
        return result;
    }
}
```

## 권한 제어 — Security 연동

*심화 · Tool 안전*

- 모델이 시켜도 사용자 권한 밖이면 실행하지 않는다
- Spring Security 인가를 도구에 직접 건다

도구 호출에 `@PreAuthorize`로 권한을 검사한다. 권한 없으면 거부 — 모델의 "판단"과 실제 "실행 권한"을 분리하는 것이 안전의 핵심.

```
모델이 도구 호출 → @PreAuthorize 검사 → 권한 있으면 실행
```

권한이 없으면 거부 — 모델의 "판단"과 실제 "실행 권한"을 분리한다.

## 기본 코드 틀 — 도구 인가

*심화 · Tool 안전*

```java
@Component
class OrderTools {
    @Tool(description = "주문을 취소한다")
    @PreAuthorize("hasRole('AGENT')")   // 권한 검사
    void cancelOrder(String orderNo) {
        // 여기 도달했다면 권한이 확인된 것
        orderService.cancel(orderNo);
    }
}
```

> **주의** 읽기 도구는 넓게, 쓰기·삭제·환불 같은 위험 도구는 좁게 권한을 준다. 위험 작업은 도구가 바로 실행하지 말고 사람 승인 단계를 두는 설계도 흔하다.

## 승인 게이트 — 사람이 한 번 확인한다

*심화 · Tool 안전*

- 환불·삭제·발송처럼 되돌릴 수 없는 행동은 모델 판단만으로 실행하지 않는다
- 도구는 실행 대신 승인 요청을 만들고, 승인 후에 실제 처리
- 모델에게는 "요청이 접수됐다"고 알려 대화를 자연스럽게 이어간다

```java
@Component
class RefundTools {
    @Tool(description = "환불을 요청한다. 실제 환불은 담당자 승인 후 처리된다.")
    String requestRefund(@ToolParam(description = "주문번호") String orderId,
            @ToolParam(description = "환불 사유") String reason,
            ToolContext ctx) {
        String userId = (String) ctx.getContext().get("userId");
        Approval approval = approvalService.create(          // 실행이 아니라 접수
                Approval.of("REFUND", orderId, reason, userId));
        auditLog.record("REFUND_REQUESTED", userId, orderId, reason);
        return "환불 요청 %s 번으로 접수했습니다. 담당자 승인 후 처리됩니다."
                .formatted(approval.id());
    }
}
```

## Tool 설계 원칙

*심화 · Tool 설계·최적화*

| 원칙 | 이유 |
| --- | --- |
| 설명(description)을 명확히 | 모델이 언제·어떻게 부를지 정확히 판단 |
| 도구는 작고 단일 책임으로 | 조합이 쉽고 오용·오류가 줄어든다 |
| 부작용을 명시·최소화 | 위험 작업은 눈에 띄게, 되도록 읽기 위주로 |
| 실패를 명확한 메시지로 | 모델이 인지하고 대안·안내로 이어가게 |

## 에이전트 루프 제어 — 상한과 예산

*심화 · Tool 설계·최적화*

- 에이전트는 스스로 멈추지 않을 수 있다 — 반복·토큰·시간에 모두 상한을 건다
- 같은 도구를 같은 인자로 반복하면 진전이 없는 것 — 끊어야 한다

```java
public String runAgent(String goal, String userId) {
    var budget = new AgentBudget(8, 50_000, Duration.ofSeconds(60));  // 회·토큰·시간
    var seen = new HashSet<String>();
    for (int step = 1; budget.hasRoom(); step++) {
        ChatResponse res = chat.prompt().user(goal)
                .tools(tools).toolContext(Map.of("userId", userId))
                .call().chatResponse();
        budget.consume(res.getMetadata().getUsage(), step);
        var calls = res.getResult().getOutput().getToolCalls();
        if (calls.isEmpty()) {
            return res.getResult().getOutput().getText();     // 정상 종료
        }
        for (var c : calls) {          // 같은 호출 반복 = 진전 없음
            if (!seen.add(c.name() + c.arguments())) {
                return "요청을 완료하지 못했습니다. 조건을 좁혀 다시 요청해주세요.";
            }
        }
    }
    return "처리 시간이 길어져 중단했습니다.";
}
```

> **주의** 상한 없는 에이전트는 비용 사고로 직행한다. 실제로 무한 루프가 밤새 돌아 수백만 원이 청구된 사례가 반복해서 보고된다 — 상한은 기능이 아니라 안전장치다.

## Tool 최적화 전략

*심화 · Tool 설계·최적화*

- 도구가 많으면 모델이 고르기 어렵고 프롬프트도 길어진다
  - 상황에 필요한 도구만 선별해 등록
- 느린 도구는 타임아웃·비동기, 반복 조회는 캐시
- 여러 스텝(ReAct)은 스텝 상한을 둬 무한 반복을 막는다

> **주의** 에이전트가 스스로 여러 번 도구를 부를 때 비용·지연이 예측하기 어렵다. 최대 스텝 수·타임아웃·예산 상한을 걸어 폭주를 막는 안전장치가 필요하다.

## Tool 반환값 — 모델이 읽을 형태로

*심화 · Tool 설계·최적화*

- DB 엔티티를 그대로 반환하면 토큰만 먹고 정확도는 떨어진다
- 필요한 필드만 남긴 요약 record를 반환한다
- 목록은 건수 상한을 두고, 넘치면 "N건 중 상위 M건"이라고 알린다

| 나쁜 반환 | 무엇이 문제인가 | 좋은 반환 |
| --- | --- | --- |
| 엔티티 전체(30필드) | 불필요한 필드가 토큰을 먹고 잡음이 된다 | 필요한 5필드만 담은 record |
| 전체 목록(수백 건) | 컨텍스트를 넘겨 응답이 잘린다 | 상위 10건 + "총 N건" 안내 |
| 원시 JSON 덩어리 | 모델이 해석에 실패하기 쉽다 | 한 줄 요약 문장 + 핵심 수치 |
| null / 빈 문자열 | 모델이 상황을 설명하지 못한다 | "조회 결과 없음" 같은 명시 문구 |
| 스택트레이스 | 내부 구조가 사용자에게 새어나간다 | "일시적 오류" 같은 안전한 메시지 |

## 멀티 에이전트 — 역할을 나눈다

*심화 · Tool 설계·최적화*

- 도구가 많아지면 한 에이전트가 고르기 어려워진다
- 역할별로 나누면 각자의 도구 목록이 짧아져 선택 정확도가 오른다
- 대가는 복잡도와 비용 — 정말 필요할 때만

| 구성 | 형태 | 적합 | 주의 |
| --- | --- | --- | --- |
| 단일 에이전트 | 도구 5~7개 | 대부분의 경우 | 도구가 늘면 정확도 하락 |
| 감독자형 | 라우터가 전문가에게 위임 | 업무 영역이 뚜렷이 갈릴 때 | 라우팅 오류가 전체를 망친다 |
| 순차 파이프라인 | 역할을 순서대로 통과 | 단계가 정해진 업무 | 단계마다 지연이 누적 |
| 병렬 + 통합 | 여러 관점을 동시에 낸 뒤 합침 | 리뷰·다면 분석 | 비용이 배수로 는다 |

> **주의** 도구 5~7개를 넘어가면 단일 에이전트의 선택 정확도가 떨어지기 시작한다. 그때가 나눌 시점이지, 처음부터 멀티 에이전트로 시작할 이유는 없다.

## 정리 — 통제된 행동

*심화 · Tool 설계·최적화*

- 감사 로깅(AOP)으로 모든 도구 호출을 기록(실패 포함)
- 권한 제어(Security)로 사용자 권한 밖 실행을 차단
- 설계는 명확한 설명·단일 책임, 최적화는 선별·캐시·스텝 상한

> **정리** 행동하는 AI의 핵심은 통제다 — 모델의 판단과 실행 권한을 분리하고, 기록하고, 상한을 둔다. 다음은 이 전체를 운영에 올리는 성능·배포 심화다.

## 도구 테스트 전략

*심화 · Tool 설계·최적화*

- 도구는 모델 없이 테스트할 수 있고, 반드시 해야 한다
- 특히 권한 검증은 모델을 거치지 않고 직접 확인한다
- "모델이 알아서 안 부르겠지"는 검증이 아니다

| 무엇을 | 어떻게 | 모델 호출 |
| --- | --- | --- |
| 권한 격리 | 타인 ID로 호출해 차단되는지 | 없음 — 직접 호출 |
| 입력 검증 | 허용 목록 밖 값을 넘겨본다 | 없음 |
| 실패 처리 | 예외 대신 메시지가 오는지 | 없음 |
| 반환 형식 | 모델이 읽을 문장인지 | 없음 — 사람이 읽어본다 |
| 쓰기 계약 | 실행이 아니라 접수만 되는지 | 없음 |
| 도구 선택 | 적절한 상황에 불리는지 | 있음 — 소량·주기적 |

```java
@Test
void 타인_주문은_차단된다() {                       // 모델이 필요 없다
    String result = tools.orderStatus("99999", new ToolContext(Map.of("userId", "user1")));
    assertThat(result).isEqualTo("해당 주문번호를 찾을 수 없습니다. 주문번호를 다시 확인해주세요.");
}
```

> **체크** 도구 테스트의 90%는 모델 없이 된다. 모델을 부르는 것은 "적절한 상황에 불리는가" 하나뿐이고, 그건 골든셋에서 함께 본다.

## 미니 실습 — 감사·인가·승인

*심화 · Tool 설계·최적화*

- 도구에 세 겹의 통제를 두른다
- 없어도 도는 것들 — 그래서 빠뜨린다
- ch10/toolsafe를 참고한다

| 단계 | 무엇을 한다 | 확인 기준 |
| --- | --- | --- |
| ① | AOP로 도구 호출 감사 로깅 추가 | 도구명·인자·사용자·결과가 남는다 |
| ② | 인자에 개인정보가 있으면 마스킹 | 로그에 원문이 남지 않는다 |
| ③ | 도구에 `@PreAuthorize`로 인가 적용 | 권한 없는 호출이 막힌다 |
| ④ | 환불 도구를 접수(PENDING)까지만 동작하게 | 즉시 처리되지 않는다 |
| ⑤ | /ch10/approvals로 대기 목록 확인 후 승인 | 승인 후에만 처리된다 |
| ⑥ | 루프 상한(최대 도구 호출 수)을 걸고 초과 유도 | 상한에서 멈춘다 |

> **체크** ①~⑥ 전부 "없어도 데모는 돌아가는" 것들이다. 그래서 일정에 쫓기면 가장 먼저 빠진다 — 그리고 빠뜨리면 기능 부족이 아니라 사고가 된다.

## 실습 코드 — 간식 주문 승인 게이트

*심화 · Tool 설계·최적화*

- 도구가 할 수 있는 최대치를 "접수"로 못 박는다
- 모든 도구 호출은 자동으로 기록된다
- 승인 API는 도구 목록에 없다 — 모델이 못 부른다

```java
@Aspect @Component
class ToolAudit {                                        // ① 도구마다 로그를 넣지 않는다
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    Object 기록(ProceedingJoinPoint p) throws Throwable {
        String 이름 = p.getSignature().getName();
        try {
            Object r = p.proceed();
            log.info("[감사] {} {} 성공", 사용자(), 이름);
            return r;
        } catch (Exception e) {
            log.warn("[감사] {} {} 실패 {}", 사용자(), 이름, e.getMessage());
            throw e;
        }
    }
}

@Component
class SnackTools {
    @Tool(description = "간식을 주문한다. 즉시 결제되지 않고 팀장 승인 후 처리된다.")
    public String 간식주문(@ToolParam(description = "품목과 수량") String 품목,
            ToolContext ctx) {
        String 사용자 = (String) ctx.getContext().get("userId");  // ② ID는 모델이 아니라 여기서
        var 티켓 = tickets.create(사용자, 품목);                   // ③ 접수만 — 상태 PENDING
        return "%s 주문 접수(%s). 팀장 승인 후 결제됩니다.".formatted(품목, 티켓.no());
    }
}

// "초코바 3개 주문해줘" → "초코바 3개 주문 접수(T-0007). 팀장 승인 후 결제됩니다."
// 승인은 사람이 — POST /lab11/approve?no=T-0007   ← 도구 목록에 없으니 모델은 부를 수 없다
```

## 실행·테스트 — 승인 게이트

*심화 · Tool 설계·최적화*

- 주문이 즉시 처리되면 실패다 — 접수만 되어야 정상이다
- 감사 로그에 누가 무엇을 요청했는지 남았는지 본다
- 테스트는 승인 없이 결제가 되지 않는지를 확인한다

```bash
# 1) 파일 위치
src/main/java/com/skala/lab11/SnackTools.java · ToolAudit.java
#    → 실행: SpringAI_실습/11_승인게이트 폴더를 VS Code로 열고 F5 (또는 ./gradlew bootRun)

# 2) 주문해본다 — 즉시 처리되지 않아야 정상
curl -u user1:pass 'localhost:8080/lab11/ask?q=초코바 3개 주문해줘'
#   "초코바 3개 주문 접수(T-0007). 팀장 승인 후 결제됩니다."

# 3) 대기 목록과 승인(승인은 사람만)
curl -u admin:admin localhost:8080/lab11/tickets/pending
curl -u admin:admin -X POST 'localhost:8080/lab11/approve?no=T-0007'

# 4) 뚫어보기 — 막히는지 직접 확인한다
curl -u user1:pass 'localhost:8080/lab11/ask?q=승인까지 네가 해줘'   # 거절돼야 정상
curl -u user1:pass -X POST 'localhost:8080/lab11/approve?no=T-0007' # 403

# 5) 감사 로그 확인
#   [감사] user1 간식주문 성공   ← 도구명·사용자·결과가 남는다

# 6) 테스트 — 접수까지만 되는지 검증한다
@Test void 주문은_접수까지만_된다() {
    var 결과 = tools.간식주문("초코바 3개", ctx("user1"));
    assertThat(결과).contains("접수");
    assertThat(tickets.find("T-0007").status()).isEqualTo(PENDING);   // 처리 안 됨
}

# 안 되면 — 즉시 처리됨: 도구가 확정까지 하고 있다 · 403 안 뜸: @PreAuthorize 확인
```

## 핵심 요약 — Tool 안전과 설계

*심화 · Tool 설계·최적화*

- 이 장의 결론 — 자율성의 크기는 되돌릴 수 있는 정도에 맞춘다
- 조회는 자유롭게, 쓰기는 제한적으로, 되돌릴 수 없는 일은 승인을 거쳐

| 장치 | 무엇을 막나 | 구현 지점 |
| --- | --- | --- |
| 감사 로깅 | 무슨 일이 있었는지 모르는 상황 | AOP 또는 가장 바깥 Advisor |
| 마스킹 | 로그에 쌓이는 개인정보 | 보존 기간과 함께 먼저 정한다 |
| 권한 제어 | 도구를 통한 권한 우회 | ToolContext + 쿼리 조건 |
| 승인 게이트 | 되돌릴 수 없는 자동 실행 | 도구는 접수만, 처리는 사람이 |
| 입력 검증 | 모델이 넘긴 이상한 값 | 허용 목록으로 좁힌다 |
| 도구 수 제한 | 선택 정확도 저하·토큰 낭비 | 한 번에 5~7개 이내 권장 |

> **체크** 환불·삭제·발송 도구가 즉시 실행된다면 그것은 기능이 아니라 사고 대기 상태다.
