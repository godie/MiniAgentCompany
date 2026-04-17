package com.ideapipeline.model;

import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.Seniority;
import java.util.List;
import java.util.stream.Stream;

public record Team(List<TeamMember> members) {
    public Stream<TeamMember> byRole(BaseRole role) {
        return members.stream().filter(member -> member.baseRole() == role);
    }
    public List<TeamMember> leads() {
        return members.stream()
                .filter(member -> member.seniority().ordinal() >= Seniority.LEAD.ordinal())
                .toList();
    }
}
