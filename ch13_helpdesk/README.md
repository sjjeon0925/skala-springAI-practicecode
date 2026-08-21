# ch13_helpdesk — 종합실습: HelpDesk AI

RAG(사내 규정 문서 근거 답변) · Tool Calling(주문 조회) · 승인 게이트(환불 접수) ·
대화 메모리 · 구조화 응답 · 인증/인가 · 관찰(계측·감사 로그·골든셋 평가)을
하나의 서비스로 합친 캡스톤 실습이다.

**혼자 돈다.** 이 폴더만 열어 `./gradlew bootRun` 하면 끝이고, 다른 장 폴더를 참조하지 않는다.

---

## 실행

```bash
export OPENAI_API_KEY="sk-..."   # 소스·깃에 절대 커밋하지 않는다
./gradlew bootRun          # http://localhost:8080
```

- Swagger UI — <http://localhost:8080/swagger-ui.html> (우측 상단 **Authorize**로 로그인 후 [Try it out])
- 계정: `user-1`/`pass`, `user-2`/`pass`(일반 사용자) · `admin`/`admin`(담당자)
- 실행 순서와 화면 캡처용 시나리오는 **`running-scenarios.md`** 참고

---

## 무엇이 들어 있나 (Phase ↔ 파일)

```
com.skala.helpdesk
├─ HelpDeskApplication.java
├─ config/       AiConfig.java            // Phase 1 — ChatClient·Advisor 조립
│                HelpDeskProperties.java  //           설정 외부화(top-k·threshold)
│                VectorStoreConfig.java   //           인메모리 VectorStore
├─ chat/         HelpDeskService.java     // Phase 3·5 — 업무 흐름(Service)
│                AnswerDto.java           // Phase 6 — 구조화 응답(DTO)
├─ repository/   OrderRepository.java     // Phase 4 — 데이터 접근
├─ rag/          IngestService.java       // Phase 2 — 문서 → 청크 → 벡터
├─ tools/        OrderTools.java          // Phase 4 — 주문 조회
│                TicketTools.java         // Phase 4 — 티켓 접수(승인 게이트)
├─ advisor/      TokenMeterAdvisor.java   // Phase 8 — 토큰·지연 계측
│                ToolAuditAspect.java     // Phase 7 — 감사 로깅(AOP)
│                ToolCallTracker.java     // Phase 6 — 이번 요청의 도구 사용 여부 기록
└─ web/          ChatController.java      // Phase 6·7 — REST + SSE
                 AdminController.java     // Phase 2·2심화·4 — 인제스트·품질확인·승인
                 SecurityConfig.java      // Phase 7 — 인증·인가
                 OpenApiConfig.java       //           Swagger Authorize 버튼

src/test/java/.../eval/GoldenSetEvalTest.java   // Phase 8 — 골든셋 품질 기준선
```

```
요청:  TokenMeter → SafeGuard → Memory → QA(RAG) → Logger → 모델 → (필요시 Tool)
응답:  모델 → Logger → QA → Memory → SafeGuard → TokenMeter
```

**Advisor 순서가 중요한 이유** — 안전 필터(SafeGuard)를 메모리보다 뒤에 두면, 걸러야 할
문구가 이미 대화 이력에 저장된 뒤라 다음 턴에 그대로 다시 들어온다. **차단은 언제나 저장보다 앞이다.**

---

## 엔드포인트

```bash
# 담당자 — 규정 문서 적재 (안 하면 RAG 답변이 나오지 않는다)
curl -u admin:admin -X POST localhost:8080/ingest-samples

# 담당자 — 인제스트 품질 확인(무엇이 들어갔는지 점수로 확인)
curl -u admin:admin 'localhost:8080/inspect?q=배송비'

# 상담 — RAG(근거 답변) · Tool(주문 조회) · 승인 게이트(환불)를 한 API에서 다룬다
curl -u user-1:pass 'localhost:8080/chat?q=단순 변심 반품은 며칠 이내인가요&sessionId=s1'

# 상담 — SSE 스트리밍판(첫 글자를 빨리 보여준다)
curl -N -u user-1:pass 'localhost:8080/chat/stream?q=배송은 얼마나 걸리나요&sessionId=s1'

# 담당자 — 대기 중인 환불 요청 승인
curl -u admin:admin 'localhost:8080/admin/tickets/pending'
curl -u admin:admin -X POST 'localhost:8080/admin/tickets/approve?id=AP-1001'

# 계측 — 토큰·지연·도구 호출
curl 'localhost:8080/actuator/metrics/ai.tokens'
curl 'localhost:8080/actuator/metrics/ai.tool.calls'
```

같은 요청이 **`http/ch13_helpdesk.http`**에 들어 있다 — VS Code REST Client 확장에서
각 요청 위의 **[Send Request]**를 누르면 curl 없이 응답을 볼 수 있다.

---

## 품질 기준선(골든셋)

```bash
./gradlew test --tests "com.skala.helpdesk.eval.GoldenSetEvalTest"
```

`src/test/resources/eval/golden.json`의 문항 8개(RAG 2 · Tool 3 · 승인게이트 1 · 거절 2)에 대해
`HelpDeskService.ask()`를 직접 호출해 답변에 필수 키워드가 포함되는지로 pass/fail을 판정한다.
보통 6~7/8 통과하며, 실패하는 1건은 대개 RAG 패러프레이즈(질문 표현 변형)에 대한 검색 한계다.

---

## 참고

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle`의 `springAiVersion` 한 줄).
- pgvector가 아니라 인메모리 `SimpleVectorStore`를 쓴다 — 앱을 재시작하면 `/ingest-samples`부터 다시 해야 한다.
- 노트(`../notes/13-종합실습-helpdesk-ai.md`)와의 차이·생략한 부분은 `running-scenarios.md` 맨 아래 "참고" 섹션에 정리돼 있다.
- 다른 장은 `../chNN_*/`에 있다.
