package com.persida.pathogenicity_calculator.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;

@Data
public class EvidenceDocUpdateEvent {
    @Pattern(regexp = "^[0-9]+$")
    private Integer interpretationId;
    private Integer conditionId;
    private String condition;
    private Integer inheritanceId;
    private String inheritance;
}
