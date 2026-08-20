package com.skala.ch09;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 8장 — 가장 단순한 형태의 도구.
 *
 * <p>도구를 등록해도 모델이 <b>항상 부르는 것은 아니다</b>. 필요하다고 판단할 때만 부른다.
 * "서울 날씨 어때?" 에는 부르고, "안녕하세요" 에는 부르지 않는다.
 */
@Component
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    /** 데모용 고정 데이터. 실제로는 외부 날씨 API 를 호출한다. */
    private static final Map<String, String> WEATHER = Map.of(
            "서울", "맑음, 29도, 습도 55%",
            "부산", "흐림, 27도, 습도 78%",
            "제주", "비, 25도, 습도 88%");

    @Tool(description = "지정한 도시의 현재 날씨를 조회한다. 한국 주요 도시만 지원한다.")
    public String currentWeather(
            @ToolParam(description = "도시 이름(예: 서울, 부산, 제주)") String city) {

        log.info("[TOOL] currentWeather city={}", city);
        return WEATHER.getOrDefault(city, city + " 의 날씨 정보는 제공하지 않습니다.");
    }

    @Tool(description = "섭씨 온도를 화씨로 변환한다.")
    public String celsiusToFahrenheit(
            @ToolParam(description = "섭씨 온도") double celsius) {

        log.info("[TOOL] celsiusToFahrenheit c={}", celsius);
        return "%.1f°C 는 %.1f°F 입니다.".formatted(celsius, celsius * 9 / 5 + 32);
    }
}
