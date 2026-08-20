# ch11_advisors — 교재 12장 · Advisor 순서 · 메모리 · 토큰 계측

Advisor 체인 조립 순서, 대화 메모리, 토큰 계측.

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
| `MemoryChatConfig.java` | **Advisor 체인 조립** — 이 장의 본체 |
| `TokenMeterAdvisor.java` | 직접 만든 `CallAdvisor` — 토큰·지연을 Micrometer 로 |
| `AdvisorController.java` | `/ch11/**` — 체인을 눈으로 확인하는 엔드포인트 |
| `IngestService.java` | (8장에서 복사) QA Advisor 가 쓸 근거 문서 적재용 |

```
요청:  TokenMeter → SafeGuard → Memory → QA → Logger → 모델
응답:  모델 → Logger → QA → Memory → SafeGuard → TokenMeter
```

**순서가 중요한 이유** — 안전 필터를 메모리보다 뒤에 두면, 걸러야 할 문구가
이미 대화 이력에 저장된 뒤다. 다음 턴에 그대로 다시 들어온다.
**차단은 언제나 저장보다 앞이다.**

---

## 실행해 보기

```bash
# ① QA Advisor 가 참고할 문서를 넣는다
curl -X POST localhost:8080/ch11/ingest-samples

# ② 근거로 답한다 (QA Advisor)
curl 'localhost:8080/ch11/chat?q=단순 변심 반품은 며칠 이내인가요&sessionId=demo'

# ③ 같은 sessionId 로 이어 묻는다 — 앞 대화를 기억한다 (Memory Advisor)
curl 'localhost:8080/ch11/chat?q=그럼 배송비는 누가 내나요&sessionId=demo'

# ④ sessionId 를 바꾸면 처음 만난 것처럼 답한다
curl 'localhost:8080/ch11/chat?q=그럼 배송비는 누가 내나요&sessionId=other'

# ⑤ 민감정보 — 모델에 닿기 전에 차단된다 (SafeGuard Advisor)
curl 'localhost:8080/ch11/chat?q=내 주민등록번호 알려줘&sessionId=demo'

# ⑥ 위 호출들의 토큰이 쌓여 있다 (TokenMeter Advisor)
curl 'localhost:8080/ch11/metrics'
curl 'localhost:8080/actuator/metrics/ai.tokens'
```

> **확인해 볼 것** — ⑤ 는 **비용이 0이다.** SafeGuard 가 모델 호출 자체를 막기
> 때문에 `/ch11/metrics` 의 토큰이 오르지 않는다. 필터의 위치가 곧 비용이다.

같은 요청이 **`http/ch11_advisors.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- 원래 이 장은 설정 클래스만 있었고 호출은 14장 엔드포인트로 했다. 독립 실행을 위해 `AdvisorController` 와 문서 적재용 `IngestService`(8장에서 복사)를 넣었다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
