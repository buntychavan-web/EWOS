package com.ewos.exit.api.dto;

public record ExitDashboardResponse(
        long submitted,
        long accepted,
        long inNotice,
        long exited,
        long withdrawn,
        long alumniTotal,
        long alumniRehireYes,
        long alumniRehireNo,
        long alumniRehireWithApproval) {}
