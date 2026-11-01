package com.carix.profile.guest;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.profile.service.UserDataEraser;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GuestCleanupJob.class, UserDataEraser.class})
class GuestCleanupJobTest {

  private static final OffsetDateTime NOW =
      OffsetDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private GuestCleanupJob job;
  @Autowired private UserDataEraser eraser;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CareerProfileRepository careerProfileRepository;
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUpAuthTables() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS auth");
    jdbc.execute("DROP TABLE IF EXISTS auth.session");
    jdbc.execute("DROP TABLE IF EXISTS auth.users");
    jdbc.execute(
        "CREATE TABLE auth.users (id UUID PRIMARY KEY, guest BOOLEAN,"
            + " created_at TIMESTAMP WITH TIME ZONE)");
    jdbc.execute(
        "CREATE TABLE auth.session (user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,"
            + " expires_at TIMESTAMP WITH TIME ZONE)");
  }

  @Test
  @DisplayName("findExpiredGuests - 세션이 모두 만료된 게스트만 고른다")
  void 세션이_모두_만료된_게스트만_고른다() {
    UUID expiredGuest = insertUser(true, NOW.minusHours(25));
    insertSession(expiredGuest, NOW.minusHours(1));
    UUID activeGuest = insertUser(true, NOW.minusHours(25));
    insertSession(activeGuest, NOW.plusHours(1));
    insertUser(true, NOW.minusMinutes(1)); // 세션 INSERT 직전의 막 만든 게스트
    insertUser(false, NOW.minusDays(40));

    assertThat(job.findExpiredGuests(NOW)).containsExactly(expiredGuest);
  }

  @Test
  @DisplayName("eraseGuest - 그사이 정식으로 전환된 사용자는 지우지 않는다")
  void 그사이_정식으로_전환된_사용자는_지우지_않는다() {
    UUID converted = insertUser(false, NOW.minusDays(2));
    careerProfileRepository.saveAndFlush(new CareerProfile(converted));

    assertThat(eraser.eraseGuest(converted)).isFalse();

    assertThat(count("auth.users", converted)).isEqualTo(1);
    assertThat(careerProfileRepository.findByUserId(converted)).isPresent();
  }

  @Test
  @DisplayName("eraseGuest - 게스트를 지우면 core 데이터와 세션이 함께 사라진다")
  void 게스트를_지우면_core_데이터와_세션이_함께_사라진다() {
    UUID guest = insertUser(true, NOW.minusHours(30));
    insertSession(guest, NOW.minusHours(6));
    careerProfileRepository.saveAndFlush(new CareerProfile(guest));

    assertThat(eraser.eraseGuest(guest)).isTrue();

    assertThat(count("auth.users", guest)).isZero();
    assertThat(count("auth.session", guest)).isZero();
    assertThat(careerProfileRepository.findByUserId(guest)).isEmpty();
  }

  private UUID insertUser(boolean guest, OffsetDateTime createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO auth.users (id, guest, created_at) VALUES (?, ?, ?)", id, guest, createdAt);
    return id;
  }

  private void insertSession(UUID userId, OffsetDateTime expiresAt) {
    jdbc.update("INSERT INTO auth.session (user_id, expires_at) VALUES (?, ?)", userId, expiresAt);
  }

  private int count(String table, UUID userId) {
    String column = table.equals("auth.users") ? "id" : "user_id";
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, userId);
  }
}
