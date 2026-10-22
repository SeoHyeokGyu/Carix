package com.carix.profile;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.skill.domain.Skill;
import com.carix.skill.repository.SkillRepository;
import com.carix.support.H2DataVersionIssuerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(H2DataVersionIssuerConfiguration.class)
class OnboardingFlowTest {

  private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

  @Autowired private MockMvc mockMvc;
  @Autowired private SkillRepository skillRepository;

  @BeforeEach
  void setUp() {
    if (skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("Spring Boot").isEmpty()) {
      skillRepository.save(Skill.createStandard("Spring Boot", "BACKEND"));
    }
  }

  @Test
  void 신규_사용자의_전체_온보딩_시나리오_동작을_검증한다() throws Exception {
    // 1. 커리어 프로필 수정/저장
    mockMvc
        .perform(
            patch("/v1/profile")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "jobFamily": "DEVELOPER",
                                  "yearsOfExperience": 5,
                                  "region": "SEOUL_GANGNAM",
                                  "employmentType": "FULL_TIME",
                                  "companySize": "MEDIUM"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("프로필이 수정되었습니다."))
        .andExpect(jsonPath("$.data.jobFamily").value("DEVELOPER"))
        .andExpect(jsonPath("$.data.yearsOfExperience").value(5))
        .andExpect(jsonPath("$.data.region").value("SEOUL_GANGNAM"));

    // 2. 최초 소득 이력 정보 등록
    mockMvc
        .perform(
            post("/v1/income")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "grossSalary": 65000000,
                                  "netSalary": 4500000,
                                  "effectiveDate": "2026-07-01"
                                }
                                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.grossSalary").value(65000000))
        .andExpect(jsonPath("$.data.netSalary").value(4500000))
        .andExpect(jsonPath("$.data.effectiveDate").value("2026-07-01"));

    // 3. 지출 내역 등록
    mockMvc
        .perform(
            put("/v1/expenses/202607")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "items": [
                                    { "category": "HOUSING", "amount": 600000 },
                                    { "category": "FOOD", "amount": 400000 }
                                  ]
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.baseYearMonth").value("202607"))
        .andExpect(jsonPath("$.data.weightStatus").value("PARTIALLY_ESTIMATED"));

    // 4. 보유 스킬 등록
    mockMvc
        .perform(
            post("/v1/user-skills")
                // 게이트웨이가 세션 검증 후 넣는 헤더. 없으면 401 이다.
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "standardName": "Spring Boot",
                                  "monthsOfExperience": 24,
                                  "lastUsedDate": "2026-07-01"
                                }
                                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.standardName").value("Spring Boot"))
        .andExpect(jsonPath("$.data.monthsOfExperience").value(24))
        .andExpect(jsonPath("$.data.lastUsedDate").value("2026-07-01"));
  }

  @Test
  void 컬럼_너비를_넘는_필드로_프로필을_수정하면_400_이다() throws Exception {
    // career_profile.job_family 는 varchar(100) 이다 (deploy/postgres/schema/02_user_domain.sql).
    // 검증 없이 그대로 저장 시도하면 DataIntegrityViolationException -> 500 이 난다.
    String userId = "00000000-0000-0000-0000-000000000003";
    String tooLongJobFamily = "가".repeat(101);

    mockMvc
        .perform(
            patch("/v1/profile")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobFamily\":\"" + tooLongJobFamily + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 프로필을_한번도_저장하지_않은_사용자를_조회하면_경력연차가_null_이다() throws Exception {
    // GET /v1/profile 은 가입 직후 앱이 가장 먼저 호출하는 API다 — career_profile 행이 없는 경로가
    // 실제로 HTTP 로 커버되는지 확인한다. 0 이 아니라 null 이어야 한다 (기술 명세 §1.2 NULL 허용 원칙).
    String userId = "00000000-0000-0000-0000-000000000004";

    mockMvc
        .perform(get("/v1/profile").header("X-User-Id", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.yearsOfExperience").value(nullValue()));
  }
}
