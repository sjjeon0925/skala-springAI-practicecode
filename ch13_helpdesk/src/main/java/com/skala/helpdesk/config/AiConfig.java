package com.skala.helpdesk.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.skala.helpdesk.advisor.TokenMeterAdvisor;
import com.skala.helpdesk.tools.OrderTools;
import com.skala.helpdesk.tools.TicketTools;

/**
 * 13장 Phase 1 — 설정과 ChatClient·Advisor 체인 조립.
 *
 * <p>Advisor 는 양파 껍질처럼 요청을 감싼다. order 가 낮은 것이 바깥이고,
 * 요청은 바깥에서 안으로, 응답은 안에서 바깥으로 흐른다.
 *
 * <pre>
 *   요청:  TokenMeter → SafeGuard → Memory → QA → Logger → 모델
 *   응답:  모델 → Logger → QA → Memory → SafeGuard → TokenMeter
 * </pre>
 *
 * <p><b>순서가 중요한 이유</b>: 안전 필터를 메모리보다 뒤에 두면, 걸러야 할 문구가
 * 이미 대화 이력에 저장된 뒤다. 다음 턴에 그대로 다시 들어온다.
 * 차단은 언제나 저장보다 앞이다.
 *
 * <p>도구 호출 감사는 {@code advisor.ToolAuditAspect}(AOP)가 별도로 담당한다 —
 * Advisor 체인에 넣지 않아도 모든 {@code @Tool} 호출이 자동으로 잡힌다.
 */
@Configuration
@EnableConfigurationProperties(HelpDeskProperties.class)
public class AiConfig {

    /**
     * 개발·단일 인스턴스에서는 인메모리로 충분하다.
     * 운영에서 인스턴스가 두 대가 되는 순간 대화가 왔다 갔다 하므로
     * JDBC·Redis 리포지토리로 바꿔야 한다(build.gradle 의 주석 참고).
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)            // 길어진 대화는 잘라 토큰을 통제한다
                .build();
    }

    /** RAG · 메모리 · 안전 · 계측을 모두 붙인 "완성형" 클라이언트. */
    @Bean
    public ChatClient assistantClient(ChatClient.Builder builder,
                                      VectorStore vectorStore,
                                      ChatMemory chatMemory,
                                      TokenMeterAdvisor tokenMeter,
                                      OrderTools orderTools,
                                      TicketTools ticketTools,
                                      HelpDeskProperties props) {
        return builder
                .defaultSystem("""
                        너는 사내 업무 도우미다.
                        - 근거 문서가 주어지면 그 안의 내용만으로 답한다.
                        - 근거에서 찾을 수 없으면 모른다고 말한다.
                        - 개인 식별 정보는 절대 다시 출력하지 않는다.""")

                .defaultAdvisors(
                        tokenMeter,                                          // order  10
                        SafeGuardAdvisor.builder()                           // 입력 차단
                                .sensitiveWords(List.of("주민등록번호", "카드번호", "비밀번호"))
                                .failureResponse("죄송합니다. 민감정보가 포함된 요청은 처리할 수 없습니다.")
                                .order(100)
                                .build(),
                        MessageChatMemoryAdvisor.builder(chatMemory)         // 맥락 주입
                                .order(200)
                                .build(),
                        QuestionAnswerAdvisor.builder(vectorStore)           // 근거 주입
                                .searchRequest(SearchRequest.builder()
                                        .topK(props.rag().topK())
                                        .similarityThreshold(props.rag().threshold())
                                        .build())
                                .order(300)
                                .build(),
                        new SimpleLoggerAdvisor())                           // 최종 요청 로깅
                .defaultTools(orderTools, ticketTools)
                .build();
    }
}
