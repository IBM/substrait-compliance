package io.substrait.compliance.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages storage and retrieval of test reports.
 * Handles both private (full data) and public (anonymized) storage.
 */
public class StorageManager {
  private final StorageConfig config;
  private final ObjectMapper objectMapper;
  
  private static final String PRIVATE_DIR = "private";
  private static final String PUBLIC_DIR = "public";
  private static final String EXECUTION_RECORDS_FILE = "execution-records.json";
  private static final String ENGINE_REPORT_FILE = "engine-report.json";
  private static final String COMMUNITY_REPORT_FILE = "community-report.json";
  private static final String METADATA_FILE = "metadata.json";

  public StorageManager(StorageConfig config) {
    this.config = Objects.requireNonNull(config, "config cannot be null");
    this.objectMapper = createObjectMapper();
  }

  public StorageManager() {
    this(StorageConfig.defaultConfig());
  }

  private ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    // Note: For full Java 8 time support, add jackson-datatype-jsr310 dependency
    // and uncomment: mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    return mapper;
  }

  /**
   * Save a private (full) engine test report.
   */
  public void savePrivateReport(EngineTestReport report) throws IOException {
    Path reportDir = getPrivateReportPath(
        report.getEngineName(), 
        report.getReportTimestamp()
    );
    
    Files.createDirectories(reportDir);
    
    // Save full engine report
    Path reportFile = reportDir.resolve(ENGINE_REPORT_FILE);
    objectMapper.writeValue(reportFile.toFile(), report);
    
    // Save execution records separately for easier access
    Path recordsFile = reportDir.resolve(EXECUTION_RECORDS_FILE);
    objectMapper.writeValue(recordsFile.toFile(), report.getExecutionRecords());
    
    // Save metadata
    saveMetadata(reportDir, report);
  }

  /**
   * Save a public (anonymized) community report.
   */
  public void savePublicReport(CommunityReport report) throws IOException {
    Path reportDir = getPublicReportPath(
        report.getEngineName(),
        report.getReportTimestamp()
    );
    
    Files.createDirectories(reportDir);
    
    // Save community report
    Path reportFile = reportDir.resolve(COMMUNITY_REPORT_FILE);
    objectMapper.writeValue(reportFile.toFile(), report);
    
    // Save metadata
    savePublicMetadata(reportDir, report);
  }

  /**
   * Load a private engine test report.
   */
  public EngineTestReport loadPrivateReport(String engineName, Instant timestamp) throws IOException {
    Path reportFile = getPrivateReportPath(engineName, timestamp).resolve(ENGINE_REPORT_FILE);
    
    if (!Files.exists(reportFile)) {
      throw new IOException("Report not found: " + reportFile);
    }
    
    return objectMapper.readValue(reportFile.toFile(), EngineTestReport.class);
  }

  /**
   * Load a public community report.
   */
  public CommunityReport loadPublicReport(String engineName, Instant timestamp) throws IOException {
    Path reportFile = getPublicReportPath(engineName, timestamp).resolve(COMMUNITY_REPORT_FILE);
    
    if (!Files.exists(reportFile)) {
      throw new IOException("Report not found: " + reportFile);
    }
    
    return objectMapper.readValue(reportFile.toFile(), CommunityReport.class);
  }

  /**
   * List all private reports for an engine.
   */
  public List<ReportMetadata> listPrivateReports(String engineName) throws IOException {
    Path engineDir = Paths.get(config.getStorageRoot(), PRIVATE_DIR, engineName);
    
    if (!Files.exists(engineDir)) {
      return new ArrayList<>();
    }
    
    try (Stream<Path> paths = Files.list(engineDir)) {
      return paths
          .filter(Files::isDirectory)
          .map(this::loadMetadataFromDir)
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing(ReportMetadata::getTimestamp).reversed())
          .collect(Collectors.toList());
    }
  }

  /**
   * List all public reports for an engine.
   */
  public List<ReportMetadata> listPublicReports(String engineName) throws IOException {
    Path engineDir = Paths.get(config.getStorageRoot(), PUBLIC_DIR, engineName);
    
    if (!Files.exists(engineDir)) {
      return new ArrayList<>();
    }
    
    try (Stream<Path> paths = Files.list(engineDir)) {
      return paths
          .filter(Files::isDirectory)
          .map(this::loadMetadataFromDir)
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing(ReportMetadata::getTimestamp).reversed())
          .collect(Collectors.toList());
    }
  }

  /**
   * Delete old reports based on retention policy.
   */
  public void applyRetentionPolicy(String engineName) throws IOException {
    if (config.getRetentionDays() <= 0) {
      return; // No retention policy
    }
    
    Instant cutoff = Instant.now().minusSeconds(config.getRetentionDays() * 86400L);
    
    // Clean private reports
    cleanOldReports(Paths.get(config.getStorageRoot(), PRIVATE_DIR, engineName), cutoff);
    
    // Clean public reports
    cleanOldReports(Paths.get(config.getStorageRoot(), PUBLIC_DIR, engineName), cutoff);
  }

  private void cleanOldReports(Path engineDir, Instant cutoff) throws IOException {
    if (!Files.exists(engineDir)) {
      return;
    }
    
    try (Stream<Path> paths = Files.list(engineDir)) {
      List<Path> toDelete = paths
          .filter(Files::isDirectory)
          .filter(dir -> {
            ReportMetadata metadata = loadMetadataFromDir(dir);
            return metadata != null && metadata.getTimestamp().isBefore(cutoff);
          })
          .collect(Collectors.toList());
      
      for (Path dir : toDelete) {
        deleteDirectory(dir);
      }
    }
  }

  private void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (Stream<Path> paths = Files.walk(directory)) {
        paths.sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
      }
    }
  }

  private Path getPrivateReportPath(String engineName, Instant timestamp) {
    String timestampStr = formatTimestamp(timestamp);
    return Paths.get(config.getStorageRoot(), PRIVATE_DIR, engineName, timestampStr);
  }

  private Path getPublicReportPath(String engineName, Instant timestamp) {
    String timestampStr = formatTimestamp(timestamp);
    return Paths.get(config.getStorageRoot(), PUBLIC_DIR, engineName, timestampStr);
  }

  private String formatTimestamp(Instant timestamp) {
    return DateTimeFormatter.ISO_INSTANT
        .format(timestamp)
        .replace(":", "-")
        .replace(".", "-");
  }

  private void saveMetadata(Path reportDir, EngineTestReport report) throws IOException {
    ReportMetadata metadata = ReportMetadata.builder()
        .engineName(report.getEngineName())
        .engineVersion(report.getEngineVersion())
        .timestamp(report.getReportTimestamp())
        .totalTests(report.getSummary().getTotalTests())
        .passed(report.getSummary().getPassed())
        .failed(report.getSummary().getFailed())
        .build();
    
    Path metadataFile = reportDir.resolve(METADATA_FILE);
    objectMapper.writeValue(metadataFile.toFile(), metadata);
  }

  private void savePublicMetadata(Path reportDir, CommunityReport report) throws IOException {
    ReportMetadata metadata = ReportMetadata.builder()
        .engineName(report.getEngineName())
        .engineVersion(report.getEngineVersion())
        .timestamp(report.getReportTimestamp())
        .totalTests(report.getSummary().getTotalTests())
        .passed(report.getSummary().getPassed())
        .failed(report.getSummary().getFailed())
        .build();
    
    Path metadataFile = reportDir.resolve(METADATA_FILE);
    objectMapper.writeValue(metadataFile.toFile(), metadata);
  }

  private ReportMetadata loadMetadataFromDir(Path dir) {
    Path metadataFile = dir.resolve(METADATA_FILE);
    if (!Files.exists(metadataFile)) {
      return null;
    }
    
    try {
      return objectMapper.readValue(metadataFile.toFile(), ReportMetadata.class);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Metadata about a stored report.
   */
  public static class ReportMetadata {
    private String engineName;
    private String engineVersion;
    private Instant timestamp;
    private int totalTests;
    private int passed;
    private int failed;

    public ReportMetadata() {}

    private ReportMetadata(Builder builder) {
      this.engineName = builder.engineName;
      this.engineVersion = builder.engineVersion;
      this.timestamp = builder.timestamp;
      this.totalTests = builder.totalTests;
      this.passed = builder.passed;
      this.failed = builder.failed;
    }

    public static Builder builder() {
      return new Builder();
    }

    // Getters and setters for Jackson
    public String getEngineName() { return engineName; }
    public void setEngineName(String engineName) { this.engineName = engineName; }
    
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public int getTotalTests() { return totalTests; }
    public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
    
    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }
    
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public static class Builder {
      private String engineName;
      private String engineVersion;
      private Instant timestamp;
      private int totalTests;
      private int passed;
      private int failed;

      public Builder engineName(String engineName) {
        this.engineName = engineName;
        return this;
      }

      public Builder engineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
        return this;
      }

      public Builder timestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
      }

      public Builder totalTests(int totalTests) {
        this.totalTests = totalTests;
        return this;
      }

      public Builder passed(int passed) {
        this.passed = passed;
        return this;
      }

      public Builder failed(int failed) {
        this.failed = failed;
        return this;
      }

      public ReportMetadata build() {
        return new ReportMetadata(this);
      }
    }
  }
}

// Made with Bob
