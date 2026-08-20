package com.carix.jobposting.seed;

public record JobKoreaListingFields(
    String employmentType,
    Long salaryMin,
    Long salaryMax,
    String positionLevel,
    String companyBadge,
    String postedEndDateRaw
) {

}
