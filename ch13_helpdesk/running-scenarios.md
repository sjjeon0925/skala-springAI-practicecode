# 실행 시나리오 (화면 캡처 + 한 줄 코멘트)

계정: `user-1`/`pass`, `user-2`/`pass`(일반 사용자) · `admin`/`admin`(담당자).
Swagger 우측 상단 **Authorize** 버튼으로 로그인한 뒤 아래 순서대로 호출한다.
(계정을 바꿀 땐 Authorize에서 Logout 후 다시 로그인)

| # | 로그인 | 호출 | 한 줄 코멘트 |
| --- | --- | --- | --- |
| 1 | admin | `POST /lab3/ingest-samples` | Phase 2 — 규정 문서 2건(배송·반품 정책)을 벡터 저장소에 적재 |
| 2 | admin | `GET /lab3/inspect?q=배송비` | Phase 2 심화 — 인제스트 품질을 점수·미리보기로 확인(관리자 전용) |
| 3 | user-1 | `GET /lab3/chat?q=단순 변심 반품은 며칠 이내인가요&sessionId=s1` | Phase 3 — RAG. `sources: ["return-policy.md"]`로 근거 확인 |
| 4 | user-1 | `GET /lab3/chat?q=제 주문 12345는 지금 어디예요?&sessionId=s1` | Phase 4 — Tool. `toolUsed: true`, 실시간 주문 상태 조회 |
| 5 | user-1 | `GET /lab3/chat?q=그럼 그거 반품 돼요?&sessionId=s1` | Phase 5 — Memory. 대명사("그거")를 앞 대화 맥락으로 해석 |
| 6 | user-1 | `GET /lab3/chat?q=환불로 접수해주세요&sessionId=s1` | Phase 4 — 승인 게이트. 즉시 처리 대신 티켓 접수(PENDING)로만 응답 |
| 7 | admin | `GET /lab3/admin/tickets/pending` | 담당자 화면 — 방금 접수된 티켓이 대기 상태로 보임 |
| 8 | admin | `POST /lab3/admin/tickets/approve?id=AP-...` | 사람이 승인 — 모델은 이 API를 못 부름(도구 목록에 없음) |
| 9 | user-1 | `GET /lab3/chat?q=99999 상태 알려줘&sessionId=s1` | **권한 격리** — 남의 주문이라 "찾을 수 없습니다"(403 아니라 존재 자체를 숨김) |
| 10 | user-1 | `GET /lab3/chat/stream?q=배송은 얼마나 걸리나요&sessionId=s2` | Phase 6 — SSE 스트리밍. 토큰이 조각조각 도착하는 것을 확인 |
| 11 | (로그인 불필요) | `GET /actuator/metrics/ai.tool.calls` | Phase 8 — 계측. 도구 호출이 `tool`·`result=ok/fail` 태그로 집계됨 |
| 12 | (로그인 불필요) | `GET /actuator/metrics/ai.tokens` | Phase 8 — 계측. 프롬프트·완성 토큰 누적치 |
| 13 | — | 콘솔 로그 캡처(`AI_TOOL_AUDIT`) | 감사 로그 — 도구명·인자(마스킹됨)·성공 여부가 자동 기록됨 |
| 14 | — | `./gradlew test --tests "com.skala.helpdesk.eval.GoldenSetEvalTest"` 결과 캡처 | Phase 8 — 골든셋 8문항 품질 기준선(보통 6~7/8 통과, 1건은 RAG 패러프레이즈 한계로 설명 가능) |
| 15 (선택) | user-1 | `GET /lab3/chat?q=내 주민등록번호는 123456-1234567이야&sessionId=s1` | 레드팀 — SafeGuard가 민감정보 포함 요청을 차단 |
| 16 (선택) | user-1 | `GET /lab3/inspect?q=배송비` | **인가 확인** — 일반 사용자로 관리자 전용 API 호출 시 403 Forbidden |
| 17 (선택) | (로그인 없이) | `GET /lab3/chat?q=아무거나&sessionId=s1` | **인증 확인** — 로그인 없이 호출 시 401 Unauthorized |

## Phase 대응표

| Phase | 커버 항목 | 시나리오 # |
| --- | --- | --- |
| 1 | 설정 외부화(`HelpDeskProperties`) · Advisor 체인 조립 | (전체 호출이 이 위에서 동작) |
| 2 | 문서 인제스트 · 품질 확인 | 1, 2 |
| 3 | RAG 답변 + 출처 | 3 |
| 4 | Tool 연동(주문 조회 · 티켓 승인 게이트) | 4, 6, 7, 8, 9 |
| 5 | 대화 메모리(멀티턴) | 5 |
| 6 | 구조화 응답(`AnswerDto`) · SSE | 3~9(구조화 응답), 10(SSE) |
| 7 | 인증·인가(`SecurityConfig`) | 1, 2, 7, 8(관리자 로그인), 16, 17(거부 확인) |
| 8 | 관찰(계측) · 품질 기준선(골든셋) | 11, 12, 13, 14 |

**1~14번이 필수 흐름**이고, **15~17번(선택)**은 안전장치(레드팀·인가·인증)까지 보여주고 싶을 때 추가하면 보고서가 더 탄탄해진다.

## 참고 — 노트와 다른/생략한 부분

- SSE 스트림(10번)은 토큰 텍스트만 흘려보낸다. 노트는 스트림 마지막에 `sources` 이벤트를 별도로 붙이는 형태를 제안하지만, 여기서는 구현하지 않았다 — 출처가 필요하면 동기 API(`/lab3/chat`)를 쓴다.
- 폴백 모델·시맨틱 캐시는 이 노트 범위가 아니라 `ch12_ops`에 별도로 구현돼 있다(Day3 실습 확장 과제).
- pgvector가 아니라 인메모리 `SimpleVectorStore`를 쓴다 — 재시작하면 인제스트를 다시 해야 한다(1번부터 다시).
