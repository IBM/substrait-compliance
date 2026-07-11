package io.substrait.compliance.storage;

import java.util.Objects;

/**
 * Configuration for storage behavior.
 */
public class StorageConfig {
  private final String storageRoot;
  private final int retentionDays;
  private final boolean enableCompression;
  private final boolean enableAnonymization;

  private StorageConfig(Builder builder) {
    this.storageRoot = Objects.requireNonNull(builder.storageRoot, "storageRoot cannot be null");
    this.retentionDays = builder.retentionDays;
    this.enableCompression = builder.enableCompression;
    this.enableAnonymization = builder.enableAnonymization;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create default configuration.
   */
  public static StorageConfig defaultConfig() {
    return builder()
        .storageRoot("./storage")
        .retentionDays(90)
        .enableCompression(false)
        .enableAnonymization(true)
        .build();
  }

  // Getters
  public String getStorageRoot() { return storageRoot; }
  public int getRetentionDays() { return retentionDays; }
  public boolean isEnableCompression() { return enableCompression; }
  public boolean isEnableAnonymization() { return enableAnonymization; }

  public static class Builder {
    private String storageRoot = "./storage";
    private int retentionDays = 90;
    private boolean enableCompression = false;
    private boolean enableAnonymization = true;

    public Builder storageRoot(String storageRoot) {
      this.storageRoot = storageRoot;
      return this;
    }

    public Builder retentionDays(int retentionDays) {
      this.retentionDays = retentionDays;
      return this;
    }

    public Builder enableCompression(boolean enableCompression) {
      this.enableCompression = enableCompression;
      return this;
    }

    public Builder enableAnonymization(boolean enableAnonymization) {
      this.enableAnonymization = enableAnonymization;
      return this;
    }

    public StorageConfig build() {
      return new StorageConfig(this);
    }
  }
}

