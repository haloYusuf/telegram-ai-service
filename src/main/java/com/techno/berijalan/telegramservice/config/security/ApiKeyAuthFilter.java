package com.techno.berijalan.telegramservice.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import com.techno.berijalan.telegramservice.domain.dto.response.builder.ResponseBerijalan;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String EXPECTED_API_KEY = "ABC-BUAT-CHALLENGE-YUSUF-18";

    @org.springframework.beans.factory.annotation.Value("${security.api-key}")
    private String expectedApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiKeyAuthFilter(@org.springframework.beans.factory.annotation.Value("${security.api-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    public ApiKeyAuthFilter() {
        this(EXPECTED_API_KEY);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Lewatkan request CORS preflight (OPTIONS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lewatkan favicon.ico
        if (path.endsWith("/favicon.ico")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lewatkan endpoint streaming media (karena diakses langsung oleh tag HTML <img>, <video>, dan link unduhan tanpa header kustom)
        if (path.contains(PathConstant.URI_TELEGRAM_MEDIA_STREAM)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lewatkan endpoint webhook Telegram Cloud (karena webhook Telegram tidak mengirim custom header)
        if (path.contains("/telegram/webhook/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lewatkan Swagger UI dan OpenAPI Docs
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.equals("/swagger-ui.html")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Verifikasi X-API-KEY (mendukung via header X-API-KEY atau query parameter apiKey untuk SSE EventSource)
        String providedApiKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(providedApiKey)) {
            providedApiKey = request.getParameter("apiKey");
        }
        if (!StringUtils.hasText(providedApiKey)) {
            providedApiKey = request.getParameter("api-key");
        }

        if (StringUtils.hasText(providedApiKey) && expectedApiKey.equals(providedApiKey.trim())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Jika API Key tidak ada atau salah -> kembalikan 401 Unauthorized
        log.warn("Unauthorized API access attempt: path={}, method={}, providedKey={}",
                path, method, providedApiKey);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ResponseBerijalan.ResponseAccNoPage<Object> errorResponse = new ResponseBerijalan.ResponseAccNoPage<>();
        errorResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        errorResponse.setMessage("Unauthorized: Invalid or missing API Key");
        errorResponse.setData(null);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

