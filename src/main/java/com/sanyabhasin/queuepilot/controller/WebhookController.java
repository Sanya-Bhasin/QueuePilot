package com.sanyabhasin.queuepilot.controller;

import com.sanyabhasin.queuepilot.domain.WebhookEndpoint;
import com.sanyabhasin.queuepilot.service.WebhookService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {
  private final WebhookService service;

  public WebhookController(WebhookService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WebhookEndpoint register(
      @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestBody Request request) {
    return service.register(tenantId, request.url(), request.secret());
  }

  @GetMapping
  public List<WebhookEndpoint> list(@RequestHeader("X-Tenant-ID") UUID tenantId) {
    return service.list(tenantId);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
    service.deactivate(tenantId, id);
  }

  public record Request(String url, String secret) {}
}
