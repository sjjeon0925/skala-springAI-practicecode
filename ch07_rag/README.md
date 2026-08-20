# ch07_rag — 교재 8장 · RAG 기본

인제스트(재색인·메타데이터) · QuestionAnswerAdvisor · 출처 표기.

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
| `IngestService.java` | 읽기(Tika) → 나누기 → **메타데이터 보강** → 저장. 재색인 시 중복 방지 |
| `RagService.java` | `QuestionAnswerAdvisor` · 부서 필터 · **출처 표기** |
| `RagController.java` | `/ch07/**` |
| `VectorStoreConfig.java` | 인메모리 VectorStore |
| `resources/docs/*.md` | 실습용 샘플 규정 문서 |

**인제스트 때 안 넣은 메타데이터는 나중에 넣을 수 없다.** `source`·`docType`·`dept`·
`version` 을 이때 붙여 두면 필터 검색과 출처 표기가 전부 여기서 나온다.

---

## 실행해 보기

```bash
# ① 먼저 문서를 넣는다
curl -X POST localhost:8080/ch07/ingest-samples
#   [{"source":"return-policy.md","chunks":3}, ...]

# ② 근거로 답하게 한다 — answer 와 함께 sources(문서명·버전)가 나온다
curl 'localhost:8080/ch07/ask?q=단순 변심 반품은 며칠 이내인가요'

# ③ 검색만 따로 — RAG 품질 문제의 원인을 가르는 첫 단계
curl 'localhost:8080/ch07/retrieve?q=제주도 배송비'

# ④ 문서에 없는 것을 물으면
curl 'localhost:8080/ch07/ask?q=연차는 며칠인가요'
#   → "제공된 문서에서 확인되지 않습니다"

# ⑤ 부서 필터 — 검색 범위이자 보안 경계다
curl 'localhost:8080/ch07/ask?q=반품 규정&dept=CS'
```

> **확인해 볼 것** — `ingest-samples` 를 **두 번** 실행한 뒤 `retrieve` 를 보라.
> 재색인 처리 덕에 같은 문장이 중복으로 쌓이지 않는다. 이 처리가 없으면
> 검색 결과가 같은 청크로 도배된다.

> **답이 이상할 때는 `/retrieve` 부터.** 근거 안에 답이 아예 없으면 검색 문제이고,
> 근거에 있는데 못 쓰면 생성 문제다. 고칠 곳이 완전히 다르다.

같은 요청이 **`http/ch07_rag.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- `ChatClientConfig`(4장) · `VectorStoreConfig` 를 이 프로젝트가 직접 갖는다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
