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

/**
 * 수집 이력. 어떤 원천의 어떤 항목을 언제 봤는지만 남긴다.
 *
 * <p>원문은 보관하지 않는다. 저장된 값에서 공고를 다시 파싱할 수 없으므로 파싱 결과를 고치려면 다시 수집해야 하고, 만료된 공고는 그것도 되지 않는다.
 */
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

  @Column(name = "first_seen_month", length = 6)
  private String firstSeenMonth;

  @Column(name = "last_seen_month", length = 6)
  private String lastSeenMonth;

  @Column(name = "collected_at", nullable = false)
  private Instant collectedAt;

  public RawIngest(
      String source,
      String sourceItemId,
      String firstSeenMonth,
      String lastSeenMonth,
      Instant collectedAt) {
    this(UUID.randomUUID(), source, sourceItemId, firstSeenMonth, lastSeenMonth, collectedAt);
  }
}
