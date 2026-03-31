package com.httpactions.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public class ExecutionCondition {

    @NotBlank
    private String field;

    @NotBlank
    private String operator;

    private String value;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @AssertTrue(message = "Execution condition value is required unless operator is exists or not_exists")
    public boolean isValueValid() {
        if ("exists".equals(operator) || "not_exists".equals(operator)) {
            return true;
        }
        return value != null && !value.isBlank();
    }
}
