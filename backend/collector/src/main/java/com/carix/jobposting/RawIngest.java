package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "raw_ingest")
public class RawIngest {

  @Id private UUID id;

  @Column(nullable = false, length = 20)
  private String source;

  @Column(name = "source_item_id", nullable = false, length = 100)
  private String sourceItemId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "schema_version", length = 20)
  private String schemaVersion;

  @Column(name = "first_seen_month", length = 6)
  private String firstSeenMonth;

  @Column(name = "last_seen_month", length = 6)
  private String lastSeenMonth;

  @Column(name = "collected_at", nullable = false)
  private Instant collectedAt;

  protected RawIngest() {}

  public RawIngest(
      String source,
      String sourceItemId,
      String payload,
      String schemaVersion,
      String firstSeenMonth,
      String lastSeenMonth,
      Instant collectedAt) {
    this.id = UUID.randomUUID();
    this.source = source;
    this.sourceItemId = sourceItemId;
    this.payload = payload;
    this.schemaVersion = schemaVersion;
    this.firstSeenMonth = firstSeenMonth;
    this.lastSeenMonth = lastSeenMonth;
    this.collectedAt = collectedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getSource() {
    return source;
  }

  public String getSourceItemId() {
    return sourceItemId;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getCollectedAt() {
    return collectedAt;
  }
}
