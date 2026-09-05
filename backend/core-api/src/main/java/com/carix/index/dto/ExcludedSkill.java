package com.carix.index.dto;

import com.carix.index.enums.SkillExclusionReason;
import java.util.UUID;

/**
 * 가중합에서 특별하게 처리된 스킬과 그 사유.
 *
 * <p>{@code MviResult} 의 두 목록이 이 레코드를 함께 쓴다 — 가중합에서 빠진 스킬과, 0 으로 반영돼 들어간 스킬이다. 어느 쪽인지는 담긴 목록이 결정하고
 * {@code reason} 이 이유를 말한다.
 *
 * @param skillId 표준 스킬 식별자. 사용자 정의 스킬도 {@code skill} 행이 있으므로 값이 있다
 * @param standardName 화면 표기용 이름
 * @param reason 처리 사유. 화면 문구는 C 가 사유별로 정한다
 */
public record ExcludedSkill(UUID skillId, String standardName, SkillExclusionReason reason) {}
