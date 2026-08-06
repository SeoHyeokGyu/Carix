package com.carix.crawler;

/**
 * 채용 정보를 나타내는 Immutable Java Record입니다.
 */
public record JobPosting(
    String portal,      // JOBKOREA, SARAMIN 등
    String id,          // 공고 고유 ID
    String companyName, // 회사명
    String title,       // 공고 제목
    String careerType,  // 신입, 경력, 무관
    int careerRange,    // 경력 년수
    String education,   // 학력
    String location,    // 근무지
    String link,        // 공고 상세 링크
    String createdAt    // 등록일
) {

}
