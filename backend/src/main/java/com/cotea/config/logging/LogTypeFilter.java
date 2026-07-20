package com.cotea.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * MDC에 "logType" 키가 없는 로그는 걸러낸다.
 * logback-spring.xml의 TOPIC_FILE 어펜더에 붙여서, MDC.put("logType", "...")로
 * 명시적으로 태그한 로그만 logs/{logType}.log로 별도 저장되게 한다.
 */
public class LogTypeFilter extends Filter<ILoggingEvent> {

    private static final String LOG_TYPE_KEY = "logType";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String logType = event.getMDCPropertyMap().get(LOG_TYPE_KEY);
        if (logType == null || logType.isBlank()) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
