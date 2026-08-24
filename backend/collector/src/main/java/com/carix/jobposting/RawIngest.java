package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "raw_ingest")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RawIngest {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

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

  public RawIngest(
      String source,
      String sourceItemId,
      String payload,
      String schemaVersion,
      String firstSeenMonth,
      String lastSeenMonth,
      Instant collectedAt) {
    this(
        UUID.randomUUID(),
        source,
        sourceItemId,
        payload,
        schemaVersion,
        firstSeenMonth,
        lastSeenMonth,
        collectedAt);
  }
}
