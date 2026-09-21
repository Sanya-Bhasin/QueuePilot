package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.JobDefinition;
import com.sanyabhasin.queuepilot.repository.JobDefinitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JobDefinitionService manages job type registrations.
 */
@Service
public class JobDefinitionService {

  private final JobDefinitionRepository jobDefinitionRepository;

  public JobDefinitionService(JobDefinitionRepository jobDefinitionRepository) {
    this.jobDefinitionRepository = jobDefinitionRepository;
  }

  @Transactional
  public JobDefinition registerJob(UUID tenantId, String name, Integer version, String description) {
    // Check if version already exists
    var existing = jobDefinitionRepository.findByTenantIdAndNameAndVersion(tenantId, name, version);
    if (existing.isPresent()) {
      throw new IllegalArgumentException("Job definition already exists for version " + version);
    }

    JobDefinition def = new JobDefinition(UUID.randomUUID(), tenantId, name, version, description);
    return jobDefinitionRepository.save(def);
  }

  @Transactional(readOnly = true)
  public List<JobDefinition> listActive(UUID tenantId) {
    return jobDefinitionRepository.findByTenantIdAndIsActiveTrue(tenantId);
  }

  @Transactional(readOnly = true)
  public JobDefinition get(UUID tenantId, UUID jobId) {
    return jobDefinitionRepository.findByIdAndTenantIdAndIsActiveTrue(jobId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Job definition not found"));
  }

  @Transactional
  public void deactivate(UUID tenantId, UUID jobId) {
    JobDefinition def = get(tenantId, jobId);
    def.setIsActive(false);
    jobDefinitionRepository.save(def);
  }
}
