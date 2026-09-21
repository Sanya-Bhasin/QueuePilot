package com.sanyabhasin.queuepilot.controller;

import com.sanyabhasin.queuepilot.domain.JobDefinition;
import com.sanyabhasin.queuepilot.service.JobDefinitionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/job-definitions")
public class JobDefinitionController {
  private final JobDefinitionService service;

  public JobDefinitionController(JobDefinitionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public JobDefinition create(
      @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestBody Request request) {
    return service.registerJob(tenantId, request.name(), request.version(), request.description());
  }

  @GetMapping
  public List<JobDefinition> list(@RequestHeader("X-Tenant-ID") UUID tenantId) {
    return service.listActive(tenantId);
  }

  @GetMapping("/{id}")
  public JobDefinition get(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
    return service.get(tenantId, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID id) {
    service.deactivate(tenantId, id);
  }

  public record Request(String name, Integer version, String description) {}
}
