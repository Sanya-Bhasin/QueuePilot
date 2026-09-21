package com.sanyabhasin.queuepilot.config;

import com.sanyabhasin.queuepilot.domain.Tenant;
import com.sanyabhasin.queuepilot.service.TenantApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyAuthFilter extends OncePerRequestFilter {
  private final TenantApiKeyService tenantApiKeyService;

  public ApiKeyAuthFilter(TenantApiKeyService tenantApiKeyService) {
    this.tenantApiKeyService = tenantApiKeyService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.contains("/swagger-ui")
        || path.contains("/openapi")
        || path.contains("/actuator/health")
        || path.contains("/actuator/health/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || authHeader.isBlank()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Authorization header");
      return;
    }

    String apiKey = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
    Tenant tenant = tenantApiKeyService.findByRawApiKey(apiKey).orElse(null);
    if (tenant == null || !tenant.getIsActive()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
      return;
    }

    String tenantHeader = request.getHeader("X-Tenant-ID");
    if (tenantHeader != null && !tenantHeader.isBlank() && !UUID.fromString(tenantHeader).equals(tenant.getId())) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant header does not match API key");
      return;
    }

    request.setAttribute("tenantId", tenant.getId());
    request.setAttribute("tenantName", tenant.getName());
    chain.doFilter(request, response);
  }
}
