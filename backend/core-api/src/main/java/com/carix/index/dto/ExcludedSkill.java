package com.carix.index.dto;

import com.carix.index.enums.SkillExclusionReason;
import java.util.UUID;

/**
 * 가중합에서 빠진 스킬과 그 사유.
 *
 * @param skillId 표준 스킬 식별자. 사용자 정의 스킬도 {@code skill} 행이 있으므로 값이 있다
 * @param standardName 화면 표기용 이름
 * @param reason 제외 사유. 화면 문구는 C 가 사유별로 정한다
 */
public record ExcludedSkill(UUID skillId, String standardName, SkillExclusionReason reason) {}
