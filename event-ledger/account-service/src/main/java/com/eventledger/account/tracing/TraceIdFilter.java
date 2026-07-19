package com.eventledger.account.tracing;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Extracts the trace ID propagated by the Gateway. Deliberately does NOT
 * generate a fallback UUID if the header is missing -- a request reaching
 * this service without a trace ID means the Gateway's propagation is broken,
 * and that should be visible as "unknown" in logs, not silently masked with
 * a freshly minted ID that would wrongly imply an unbroken trace.
 */
@Component
@Order(1)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        String traceId = httpReq.getHeader(TRACE_ID_HEADER);
        MDC.put(MDC_KEY, traceId != null ? traceId : "unknown");
        TraceContext.set(traceId);
        if (traceId != null) {
            httpRes.setHeader(TRACE_ID_HEADER, traceId);
        }
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
            TraceContext.clear();
        }
    }
}
