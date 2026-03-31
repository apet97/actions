package com.httpactions.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public class SuccessCondition {

    @NotBlank
    private String operator;

    private String value;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @AssertTrue(message = "Success condition value is required")
    public boolean isValueValid() {
        return value != null && !value.isBlank();
    }
}
