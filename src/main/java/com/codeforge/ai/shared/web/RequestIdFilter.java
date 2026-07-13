package com.codeforge.ai.shared.web;

import com.codeforge.ai.shared.response.ResultUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(RequestIdConstants.HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = ResultUtils.generateRequestId();
        }
        MDC.put(RequestIdConstants.MDC_KEY, requestId);
        response.setHeader(RequestIdConstants.HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIdConstants.MDC_KEY);
        }
    }
}
