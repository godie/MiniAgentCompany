package com.ideapipeline.controller.dto;

import com.ideapipeline.model.RefinementQA;
import com.ideapipeline.model.Team;

import java.util.List;

public record RunPipelineRequest(
        String idea,
        List<RefinementQA> refinements,
        Team team
) {
}