package com.carix.profile.guest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.carix.profile.service.UserDataEraser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GuestCleanupJobRunTest {

  @Test
  @DisplayName("run - 한 건이 실패해도 나머지 게스트는 계속 지운다")
  void 한_건_실패해도_나머지를_지운다() {
    UserDataEraser eraser = mock(UserDataEraser.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    GuestCleanupJob job = spy(new GuestCleanupJob(mock(JdbcTemplate.class), eraser, clock));
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    doReturn(List.of(first, second)).when(job).findExpiredGuests(any());
    doThrow(new RuntimeException("boom")).when(eraser).eraseGuest(first);

    job.run();

    verify(eraser).eraseGuest(second);
  }
}
