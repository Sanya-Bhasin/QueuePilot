package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.WebhookEndpoint;
import com.sanyabhasin.queuepilot.repository.WebhookEndpointRepository;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {
  private final WebhookEndpointRepository repository;

  public WebhookService(WebhookEndpointRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public WebhookEndpoint register(UUID tenantId, String url, String secret) {
    URI.create(url);
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("Webhook secret is required");
    }
    return repository.save(new WebhookEndpoint(UUID.randomUUID(), tenantId, url, secret));
  }

  public List<WebhookEndpoint> list(UUID tenantId) {
    return repository.findByTenantIdAndIsActiveTrue(tenantId);
  }

  @Transactional
  public void deactivate(UUID tenantId, UUID id) {
    WebhookEndpoint endpoint =
        repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Webhook not found"));
    endpoint.setIsActive(false);
    repository.save(endpoint);
  }
}
