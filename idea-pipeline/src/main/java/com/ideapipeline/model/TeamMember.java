package com.ideapipeline.model;

import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.Seniority;
import java.util.List;

public record TeamMember(
        String id,
        String name,
        BaseRole baseRole,
        Seniority seniority,
        List<String> extraResponsibilities
) {}
