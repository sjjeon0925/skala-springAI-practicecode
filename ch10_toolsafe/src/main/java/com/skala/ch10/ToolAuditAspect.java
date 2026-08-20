package com.skala.ch10;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 9장 — 감사 로깅(AOP).
 *
 * <p>{@code @Tool} 이 붙은 모든 메서드 호출을 한 곳에서 기록한다.
 * 도구마다 로깅 코드를 넣으면 반드시 빠뜨리는 곳이 생긴다.
 *
 * <p><b>주의</b>: 인자에 개인정보가 들어올 수 있다. 운영에서는 마스킹 규칙과
 * 보존 기간을 함께 정하고 시작해야 한다.
 */
@Aspect
@Component
public class ToolAuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object auditToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String tool = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName();
        String args = mask(Arrays.toString(joinPoint.getArgs()));
        long started = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            audit.info("tool={} args={} status=OK elapsedMs={}",
                    tool, args, (System.nanoTime() - started) / 1_000_000);
            return result;

        } catch (Throwable e) {
            audit.error("tool={} args={} status=FAIL error={}", tool, args, e.toString());
            throw e;
        }
    }

    /** 아주 단순한 마스킹 예시 — 실제로는 도메인에 맞는 규칙이 필요하다. */
    private String mask(String raw) {
        return raw
                .replaceAll("\\d{6}-\\d{7}", "******-*******")      // 주민등록번호 형태
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****")   // 카드번호 형태
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "***@***");             // 이메일
    }
}
