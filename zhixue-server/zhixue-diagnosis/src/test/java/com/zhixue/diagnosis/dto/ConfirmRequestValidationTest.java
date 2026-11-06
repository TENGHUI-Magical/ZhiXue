package com.zhixue.diagnosis.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确认错题入口校验：非法 errorType 必须在 MVC 参数绑定阶段返回 40000，
 * 与 ExplainRequest / ErrorRecordCreate 的防御层次保持一致。
 */
class ConfirmRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void illegalErrorTypeIsRejectedAtEntry() {
        ConfirmRequest request = request("HACK");

        assertTrue(validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .anyMatch(path -> "errorType".equals(path.toString())));
    }

    @Test
    void contractEnumIsAccepted() {
        assertTrue(validator.validate(request("CONCEPT_CONFUSION")).isEmpty());
    }

    private ConfirmRequest request(String errorType) {
        ConfirmRequest request = new ConfirmRequest();
        request.setKpId("KP0201");
        request.setErrorType(errorType);
        return request;
    }
}
