package com.ewos.succession.api.dto;

public record SuccessionDashboardResponse(
        long careerPaths,
        long promotionEligible,
        long hiPo,
        long highPerformer,
        long solidContributor,
        long developing,
        long underperformer,
        long readyNowSuccessors) {}
