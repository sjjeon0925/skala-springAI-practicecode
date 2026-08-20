package com.skala.ch06;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 호출 전용 스레드 풀.
 *
 * <p>풀 크기가 곧 <b>동시 호출 상한</b>이다. 무제한 병렬 호출은 공급자 레이트 리밋(429)을
 * 바로 만나고, 그때는 성공한 호출까지 함께 느려진다.
 * 공용 풀을 쓰지 않는 이유는 느린 AI 호출이 일반 웹 요청 처리를 굶기지 않게 하기 위해서다.
 */
@Configuration
public class AiExecutorConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);          // = 동시 호출 상한
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
