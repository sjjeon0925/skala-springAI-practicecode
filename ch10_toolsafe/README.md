# ch10_toolsafe — 교재 11장 · Tool 안전 — 승인 게이트 · 감사

승인 게이트 · AOP 감사 로깅 · 마스킹.

**혼자 돈다.** 이 폴더만 열어 `./gradlew bootRun` 하면 끝이고, 다른 장 폴더를 참조하지 않는다.

---

## 실행

```bash
export OPENAI_API_KEY="sk-..."   # 소스·깃에 절대 커밋하지 않는다
./gradlew bootRun          # http://localhost:8080
```

- Swagger UI — <http://localhost:8080/swagger-ui.html> ([Try it out] 으로 curl 없이 호출)
- 포트를 바꾸려면 `./gradlew bootRun --args='--server.port=8081'`

> 키가 없어도 **앱은 뜬다**(`${OPENAI_API_KEY:not-set}`). 모델을 실제로 부르는
> 엔드포인트에서만 401 이 난다 — Swagger 를 열어 구조부터 둘러보는 데는 지장이 없다.

---

## 무엇이 들어 있나

| 파일 | 무엇을 보나 |
| --- | --- |
| `ApprovalTools.java` | 도구가 **실행하지 않고 접수만** 한다 |
| `ToolAuditAspect.java` | `@Tool` 전부를 한곳에서 감사 로깅 + 마스킹 |
| `ToolSafeController.java` | `/ch10/**` — 모델용 도구와 담당자용 API 를 나눠 둔다 |

**자율성의 크기는 되돌릴 수 있는 정도에 맞춘다.**
조회는 자유롭게, 쓰기는 제한적으로, 되돌릴 수 없는 일은 사람의 승인을 거쳐.

---

## 실행해 보기

```bash
# 환불 요청 — 실제 환불이 아니라 승인 요청이 접수된다
curl 'localhost:8080/ch10/ask?q=주문 12345 환불해 주세요'
#   → "환불 요청 AP-1001 번으로 접수했습니다. 담당자 승인 후 ..."

# 담당자 화면 — 대기 중인 요청
curl 'localhost:8080/ch10/approvals'

# 승인
curl -X POST 'localhost:8080/ch10/approvals/approve?id=AP-1001'

# 상태 조회도 도구로
curl 'localhost:8080/ch10/ask?q=AP-1001 처리됐나요'
```

> **확인해 볼 것** — 서버 로그의 `AI_TOOL_AUDIT` 줄. 도구마다 로깅 코드를 넣으면
> 반드시 빠뜨리는 곳이 생기지만, AOP 로 걸면 빠질 수가 없다.
>
> 이메일·주민등록번호 형태를 인자에 섞어 던져 보면 로그에서 마스킹된 것이 보인다.
> **운영에서는 마스킹 규칙과 보존 기간을 함께 정하고 시작해야 한다.**

같은 요청이 **`http/ch10_toolsafe.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- `ChatClientConfig` 를 4장 프로젝트에서 복사해 왔다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
