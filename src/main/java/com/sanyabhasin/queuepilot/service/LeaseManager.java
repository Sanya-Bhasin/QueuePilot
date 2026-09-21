package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.Execution;
import com.sanyabhasin.queuepilot.domain.Lease;
import com.sanyabhasin.queuepilot.repository.ExecutionRepository;
import com.sanyabhasin.queuepilot.repository.LeaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renews active worker leases and requeues executions whose workers disappeared. */
@Service
public class LeaseManager {
  private final LeaseRepository leaseRepository;
  private final ExecutionRepository executionRepository;

  public LeaseManager(LeaseRepository leaseRepository, ExecutionRepository executionRepository) {
    this.leaseRepository = leaseRepository;
    this.executionRepository = executionRepository;
  }

  @Transactional
  public boolean renew(UUID executionId, String workerId, Instant expiry) {
    return leaseRepository
        .findByExecutionId(executionId)
        .filter(lease -> lease.getWorkerId().equals(workerId))
        .map(
            lease -> {
              lease.setLastHeartbeatAt(Instant.now());
              lease.setExpiresAt(expiry);
              leaseRepository.save(lease);
              return true;
            })
        .orElse(false);
  }

  @Scheduled(fixedDelayString = "${queuepilot.lease.recovery-interval-ms:10000}")
  @Transactional
  public void recoverExpiredLeases() {
    List<Lease> expired = leaseRepository.findByExpiresAtLessThan(Instant.now());
    for (Lease lease : expired) {
      executionRepository
          .findById(lease.getExecutionId())
          .filter(execution -> "CLAIMED".equals(execution.getStatus()))
          .ifPresent(
              execution -> {
                execution.setStatus("PENDING");
                execution.setNextAttemptAt(Instant.now());
                executionRepository.save(execution);
              });
      leaseRepository.delete(lease);
    }
  }
}
