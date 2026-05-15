package com.evcharging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Logs each API request in the format required for the cloud deployment part.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Value("${app.instance.id:unknown}")
    private String instanceId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String timestamp = Instant.now().toString();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? uri + "?" + queryString : uri;

        try {
            filterChain.doFilter(request, response);
        } finally {
            long processingTime = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            log.info("[REQUEST_LOG] timestamp={} method={} uri={} status={} duration_ms={} instance={}",
                timestamp, method, fullUri, status, processingTime, instanceId);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // These are not useful for the request log evidence.
        String uri = request.getRequestURI();
        return uri.startsWith("/h2-console") || uri.startsWith("/actuator/health");
    }
}
