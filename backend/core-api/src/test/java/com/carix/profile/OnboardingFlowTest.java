package com.carix.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.skill.domain.Skill;
import com.carix.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingFlowTest {

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
    // 1. 현재 온보딩 단계 조회 (초기 상태: 미완료, EXPENSE 단계)
    mockMvc
        .perform(get("/v1/onboarding").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.currentStep").value("EXPENSE"))
        .andExpect(jsonPath("$.data.completed").value(false));

    // 2. 커리어 프로필 수정/저장
    mockMvc
        .perform(
            patch("/v1/profile")
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
        .andExpect(jsonPath("$.data.jobFamily").value("DEVELOPER"))
        .andExpect(jsonPath("$.data.yearsOfExperience").value(5))
        .andExpect(jsonPath("$.data.region").value("SEOUL_GANGNAM"));

    // 3. 최초 소득 이력 정보 등록
    mockMvc
        .perform(
            post("/v1/income")
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

    // 4. 지출 내역 등록
    mockMvc
        .perform(
            put("/v1/expenses/202607")
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
        .andExpect(jsonPath("$.data.weightStatus").value("USER_DEFINED"));

    // 5. 보유 스킬 등록
    mockMvc
        .perform(
            post("/v1/user-skills")
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

    // 6. 온보딩 최종 완료 처리
    mockMvc
        .perform(
            patch("/v1/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "currentStep": "COMPLETED"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.currentStep").value("COMPLETED"))
        .andExpect(jsonPath("$.data.completed").value(true));
  }
}
