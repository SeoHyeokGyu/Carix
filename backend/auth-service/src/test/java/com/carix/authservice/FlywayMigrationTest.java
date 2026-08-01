package com.carix.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationTest extends AbstractIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void auth_스키마의_다섯_테이블이_모두_생성된다() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Integer tableCount = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*) FROM information_schema.tables
				WHERE table_schema = 'auth'
				AND table_name IN ('users', 'invitation', 'magic_link_token', 'session', 'rate_limit_bucket')
				""",
				Integer.class);
		assertThat(tableCount).isEqualTo(5);
	}
}
