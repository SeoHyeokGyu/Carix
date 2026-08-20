package com.carix.jobposting.seed;

import java.util.List;

public record SaraminListingFields(
    String employmentType,
    String postedEndDateRaw,
    List<String> skillTags,
    List<String> industryTags
) {

}
