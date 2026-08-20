# 13. 종합실습 — HelpDesk AI 만들기

- 시나리오와 요구사항 정의
- 아키텍처와 패키지 설계
- Phase 1~6 단계별 구현
- 검증 시나리오와 완성 코드

## 시나리오 — 무엇을 만드나

*종합실습 · 설계*

- SKALA HelpDesk AI — 사내 규정과 실시간 데이터를 함께 다루는 상담 어시스턴트
- 지금까지 배운 RAG · Tool · 메모리 · 안전 · 관찰을 하나로 조립한다

| 누가 | 무엇을 묻고 | 무엇이 필요한가 |
| --- | --- | --- |
| 고객 | "반품 규정이 어떻게 되나요?" | 사내 문서 근거 + 출처(RAG) |
| 고객 | "제 주문 12345 지금 어디예요?" | 실시간 주문 데이터(Tool) |
| 고객 | "그럼 그거 반품 돼요?" | 앞 대화의 맥락(Memory) |
| 고객 | "교환으로 바꿔주세요" | 티켓 생성 + 승인 게이트(Tool·통제) |
| 운영자 | "어제 비용이 왜 늘었지?" | 토큰·지연·오류 지표(관찰) |

## 요구사항 — 기능과 비기능

*종합실습 · 설계*

- 기능 요구는 무엇을 하는가, 비기능 요구는 어떻게 버티는가
- AI 서비스의 비기능은 정확도·비용·안전이 추가된다 — 여기서 설계가 갈린다
- 각 요구사항에 검증 방법을 붙여두면 완료 판정이 명확해진다

| 구분 | 요구사항 | 검증 방법 |
| --- | --- | --- |
| 기능 | 문서 근거로 답하고 출처를 표시한다 | 출처 없는 답변이 나오면 실패 |
| 기능 | 주문·티켓을 실시간 조회·생성한다 | 도구 호출 로그에 기록이 남는가 |
| 기능 | 3턴 이상 맥락을 유지한다 | 대명사 질문("그건")에 정상 응답 |
| 비기능 | P95 응답 5초 이내(비스트리밍) | 부하 테스트 지표 |
| 비기능 | 질의당 평균 토큰 상한 준수 | Micrometer 토큰 카운터 |
| 비기능 | 인젝션·민감어 차단, 모든 도구 호출 감사 | 레드팀 프롬프트 10종 통과 |
| 비기능 | 주 모델 장애 시 폴백으로 응답 지속 | 장애 주입 테스트 |

## 아키텍처 — 조각들을 어떻게 잇나

*종합실습 · 설계*

```
사용자
  └─ 웹 UI(SSE)
       └─ ChatController
            └─ HelpDeskService
                 └─ AI 계층
                      ├─ ChatClient
                      ├─ Advisor 체인 — QA · Memory · Safe · Audit
                      ├─ 근거 — VectorStore (사내문서 인제스트, 출처 메타데이터)
                      ├─ 행동 — @Tool (티켓 조회/생성, 주문 상태 조회)
                      └─ 운영 — Micrometer (토큰·지연·비용), 폴백 모델
```

한 요청은 Controller → Service → ChatClient → Advisor 체인을 지나며 문서 근거와 실시간 데이터를 함께 모은다.

## 패키지 구조 — 완성 코드 지도

*종합실습 · 설계*

- 1장의 Controller · Service · Repository 계층을 그대로 지킨다
- 여기에 AI 축이 더해진다 — 설정(config) · 근거(rag) · 행동(tools) · 공통(advisor)
- Phase 번호 ↔ 파일 위치 — 어디를 고칠지 헤매지 않게 나눴다

```
SpringAI_종합실습/src/main/java/com/skala/helpdesk/
├─ HelpDeskApplication.java
├─ config/       AiConfig.java            // Phase 1 — ChatClient·Advisor 조립
│                HelpDeskProperties.java  //           설정 외부화
├─ web/          ChatController.java      // Phase 6 — REST + SSE  (Controller)
│                AdminController.java     //           인제스트·승인
│                SecurityConfig.java      // Phase 7 — 인증·인가
├─ chat/         HelpDeskService.java     // Phase 3·5 — 업무 흐름 (Service)
│                AnswerDto.java           // Phase 6 — 구조화 응답 (DTO)
├─ repository/   OrderRepository.java     // Phase 4 — 데이터 접근 (Repository)
├─ rag/          IngestService.java       // Phase 2 — 문서 → 청크 → 벡터
├─ tools/        OrderTools.java          // Phase 4 — 주문 조회
│                TicketTools.java         // Phase 4 — 티켓 접수(승인)
├─ advisor/      AuditAdvisor.java        // Phase 7 — 감사 로깅
│                TokenMeterAdvisor.java   // Phase 8 — 토큰·지연 계측
└─ eval/         GoldenSet.java           // Phase 8 — 품질 기준선
```

## Phase 1 — 설정과 ChatClient 조립

*종합실습 · Phase 1~4*

- 공급자·모델·임계값을 모두 설정으로 뺀다 — 코드에 상수를 남기지 않는다
- ChatClient 하나에 Advisor 체인 전체를 기본값으로 걸어둔다

```yaml
spring.ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    chat.options: { model: gpt-4o-mini, temperature: 0.2 }
  vectorstore.pgvector: { initialize-schema: true, dimensions: 1536 }
helpdesk: { rag: { top-k: 5, threshold: 0.62 }, memory: { max: 20 } }
```

```java
@Bean
ChatClient helpDeskClient(ChatClient.Builder builder, VectorStore vs,
        ChatMemory memory, AiProperties props,
        AuditAdvisor audit, TokenMeterAdvisor meter) {
    return builder.defaultSystem(systemPrompt)           // prompts/system.st
            .defaultAdvisors(audit, meter,                   // 감사·계측(바깥)
                    SafeGuardAdvisor.builder()
                            .sensitiveWords(List.of("주민등록번호", "카드번호")).build(),
                    MessageChatMemoryAdvisor.builder(memory).build(),
                    QuestionAnswerAdvisor.builder(vs).searchRequest(
                            SearchRequest.builder().topK(props.rag().topK())
                                    .similarityThreshold(props.rag().threshold()).build()).build())
            .build();
}
```

## Phase 2 — 문서 인제스트 파이프라인

*종합실습 · Phase 1~4*

- 사내 규정 문서를 읽어 청크로 나누고 메타데이터를 붙여 저장
- 같은 문서를 다시 넣으면 중복된다 — 문서 단위 삭제 후 재삽입
- 출처 표기를 위해 source·title·version을 반드시 넣는다

```java
public IngestResult ingest(Resource file, String docType, String dept) {
    String source = file.getFilename();
    vectorStore.delete("source == '" + source + "'");     // ① 재색인 대비
    List<Document> raw = new TikaDocumentReader(file).get();
    var chunks = TokenTextSplitter.builder()
            .withChunkSize(800).withMinChunkSizeChars(350).build().apply(raw);
    List<Document> enriched = chunks.stream().map(c -> {   // ② 메타데이터
        Map<String, Object> m = new HashMap<>(c.getMetadata());
        m.put("source", source);   m.put("docType", docType);
        m.put("dept", dept);       m.put("version", today());
        return new Document(c.getText(), m);
    }).toList();
    vectorStore.add(enriched);                            // ③ 임베딩 + 저장
    return new IngestResult(source, enriched.size());
}
```

> **주의** 재색인 없이 add만 반복하면 같은 청크가 쌓인다. 검색 결과가 같은 문장으로 도배되고 근거가 다양해지지 않는다 — 문서 단위 삭제를 먼저 하라.

## Phase 2 심화 — 인제스트 품질 확인

*종합실습 · Phase 1~4*

- 인제스트는 성공 메시지가 아니라 결과물로 확인한다
- 여기서 안 잡으면 Phase 3에서 원인을 못 찾는다
- 확인 창구를 엔드포인트로 하나 열어두면 계속 쓴다

```java
@GetMapping("/api/admin/chunks")          // 무엇이 들어갔는지 눈으로 본다
@PreAuthorize("hasRole('ADMIN')")
public List<Map<String, Object>> inspect(@RequestParam String q,
        @RequestParam(defaultValue = "5") int topK) {
    var hits = vectorStore.similaritySearch(
            SearchRequest.builder().query(q).topK(topK).build());
    return hits.stream().map(d -> Map.<String, Object>of(
            "source",  d.getMetadata().get("source"),
            "version", d.getMetadata().get("version"),
            "score",   d.getScore(),                  // 유사도 — 임계값 조정 근거
            "preview", d.getText().substring(0, Math.min(160, d.getText().length()))
    )).toList();
}
```

## Phase 3 — RAG 답변과 출처 표기

*종합실습 · Phase 1~4*

- Advisor가 근거를 넣어주지만, 출처는 우리가 꺼내 붙여야 한다
- `chatClientResponse().context()`에 검색된 문서 목록이 담겨온다
- 근거가 없으면 모른다고 답하게 — 지어내기를 막는 첫 방어선

```java
public Answer ask(String question, String conversationId) {
    ChatClientResponse response = chat.prompt()
            .user(question)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .chatClientResponse();          // 응답 + Advisor 컨텍스트
    List<Document> used = (List<Document>) response.context()
            .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
    List<Source> sources = used == null ? List.of() : used.stream()
            .map(d -> new Source((String) d.getMetadata().get("source"),
                    (String) d.getMetadata().get("version")))
            .distinct().toList();
    String text = response.chatResponse().getResult().getOutput().getText();
    return new Answer(text, sources);
}
record Source(String document, String version) {}
```

## Phase 4 — Tool 연동(주문·티켓)

*종합실습 · Phase 1~4*

- 문서로 답할 수 없는 것 — 주문 상태·티켓은 도구로 가져온다
- 소유자 검증은 도구 안에서, 쓰기 도구는 승인 절차를 거친다

```java
@Component
public class OrderTools {
    @Tool(description = "주문번호로 배송 상태와 예상 도착일을 조회한다")
    String orderStatus(@ToolParam(description = "주문번호") String orderId,
            ToolContext ctx) {
        String userId = (String) ctx.getContext().get("userId");
        return orders.findOwned(orderId, userId)           // 소유자 검증 필수
                .map(o -> "주문 %s · 상태 %s · 예상 도착 %s".formatted(
                        o.id(), o.status(), o.eta()))
                .orElse("해당 주문을 찾을 수 없습니다.");
    }
}

@Component
public class TicketTools {
    @Tool(description = "교환·환불 티켓을 접수한다. 처리는 담당자 승인 후 진행된다.")
    String createTicket(@ToolParam(description = "주문번호") String orderId,
            @ToolParam(description = "EXCHANGE|REFUND") String type,
            @ToolParam(description = "사유") String reason, ToolContext ctx) {
        Ticket t = tickets.request(orderId, type, reason, userOf(ctx));
        return "티켓 %s 를 접수했습니다. 승인 후 처리됩니다.".formatted(t.no());
    }
}
```

> **주의** 모델이 넘긴 orderId는 사용자의 것이 아닐 수 있다 — 소유자 검증은 도구 안에서.

## Phase 4 심화 — 도구 설계 리뷰

*종합실습 · Phase 1~4*

- 도구가 불리기는 하는데 엉뚱하게 부르는 것이 가장 흔하다
- 대부분 설명을 고치면 해결된다 — 코드 문제가 아니다
- 다만 권한 문제는 설명으로 못 고친다 — 우리 코드를 봐야 한다

| 증상 | 원인 | 고칠 곳 |
| --- | --- | --- |
| 도구를 아예 안 부른다 | 언제 쓰는지가 설명에 없다 | "~할 때 사용한다" 추가 |
| 엉뚱한 도구를 부른다 | 설명이 서로 비슷하다 | 차이를 명시적으로 쓴다 |
| 인자를 이상하게 넘긴다 | `@ToolParam` 설명이 부족 | 형식·예시를 넣는다 |
| 같은 도구를 반복 호출 | 결과가 재시도를 유도한다 | "다시 시도" 문구 제거 |
| 남의 주문이 조회된다 | 소유자 검증 누락 | 도구 안 + 쿼리 조건 |

## Phase 5 — 메모리와 멀티턴

*종합실습 · Phase 5~6*

- "그럼 그건 반품 돼요?" — 앞 대화 없이는 답할 수 없는 질문을 처리
- conversationId는 사용자·세션 단위로 만든다 — 섞이면 사고다
- 길어진 대화는 윈도우로 잘라 토큰을 통제한다

```java
@Bean
ChatMemory chatMemory(ChatMemoryRepository repo, AiProperties props) {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repo)                 // JDBC — 재시작에도 유지
            .maxMessages(props.memory().maxMessages())  // 최근 20개
            .build();
}

// 대화 ID 규칙 — 테넌트·사용자·세션을 한 곳에서 만든다
public String conversationId(String tenantId, String userId, String sessionId) {
    return "%s:%s:%s".formatted(tenantId, userId, sessionId);
}

// 호출부 — Advisor 파라미터로 넘긴다
chat.prompt().user(question)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID,
                conversationId(tenant, user, session)))
        .call().content();
```

> **확인** 3턴 테스트를 반드시 해보라 — ① 규정 질문 ② "내 주문 12345는?" ③ "그럼 그거 반품 돼요?" — ③이 정상 응답하면 메모리·RAG·Tool이 함께 살아있다는 뜻이다.

## Phase 6 — 구조화 응답 API와 SSE

*종합실습 · Phase 5~6*

- 화면이 쓰기 좋게 답변·출처·도구 사용 여부를 나눠 반환한다
- 긴 답변은 SSE 스트리밍으로 첫 글자를 빨리 보여준다
- 스트리밍 응답에도 출처를 마지막 이벤트로 함께 내보낸다

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @PostMapping                                        // 동기 — 구조화 응답
    AnswerDto ask(@RequestBody AskRequest req, Principal user) {
        return service.ask(req.question(), user.getName(), req.sessionId());
    }

    @PostMapping(value = "/stream",                     // 스트리밍
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> stream(@RequestBody AskRequest req,
            Principal user) {
        return service.stream(req.question(), user.getName(), req.sessionId())
                .map(c -> ServerSentEvent.builder(c).event("token").build())
                .concatWith(Mono.fromCallable(() ->
                        ServerSentEvent.builder(service.lastSources(req))
                                .event("sources").build()))   // 마지막에 출처
                .timeout(Duration.ofSeconds(60));
    }
}
record AnswerDto(String answer, List<Source> sources, boolean toolUsed) {}
```

## 검증 시나리오 — 다섯 흐름

*종합실습 · 검증과 완성*

| 순서 | 사용자 입력 | 무엇이 동작하나 |
| --- | --- | --- |
| ① 규정 | "반품 규정 알려줘" | RAG 검색 → 근거 + 출처 |
| ② 후속 | "내 주문은?" | 메모리(맥락 유지) → `@Tool` 주문 조회 → 규정 대조 |
| ③ 행동 | "교환 접수해줘" | 승인 게이트 → `@Tool` 티켓 생성 → 티켓번호 응답 |

근거(RAG) · 맥락과 실시간(Memory + Tool) · 통제된 행동(승인) · 안전(방어) · 가용성(폴백)을 각각 확인한다.

## 핵심 요약 — 종합실습

*종합실습 · 검증과 완성*

- 이 장의 결론 — 실무 AI 서비스는 한 기능이 아니라 조합이다
- 새로운 기술은 없었다 — 배운 것을 한 흐름 안에서 협력시켰을 뿐이다

| Phase | 무엇을 만들었나 | 핵심 판단 |
| --- | --- | --- |
| 1 | 설정 외부화 · Advisor 체인 | 차단은 저장보다 앞 |
| 2 | 문서 인제스트 | 재색인 · 메타데이터 |
| 3 | RAG 답변 + 출처 | 응답 컨텍스트에서 근거를 꺼낸다 |
| 4 | 주문·티켓 도구 | 소유자 검증은 도구 안에서 |
| 5 | 대화 메모리 | conversationId 규칙을 한 곳에 |
| 6 | 구조화 응답 · SSE | 답변만 던지지 않는다 |
