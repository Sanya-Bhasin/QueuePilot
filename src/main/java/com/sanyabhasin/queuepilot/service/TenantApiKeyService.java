package com.sanyabhasin.queuepilot.service;

import com.sanyabhasin.queuepilot.domain.Tenant;
import com.sanyabhasin.queuepilot.repository.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TenantApiKeyService {
  private final TenantRepository tenantRepository;

  public TenantApiKeyService(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  public Optional<Tenant> findByRawApiKey(String rawApiKey) {
    if (rawApiKey == null || rawApiKey.isBlank()) {
      return Optional.empty();
    }
    return tenantRepository.findByApiKeyHash(hash(rawApiKey.trim()));
  }

  public static byte[] hash(String input) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return sha256.digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
