package com.example.platform.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:告警模版规则行 DTO Bean 校验回归测试
 *
 * 说明:曾发生 UpdateDTO.alarmLevel（Integer）误标 @NotBlank——Hibernate Validator
 *   对非 CharSequence 类型抛 UnexpectedTypeException，任何规则行更新请求都 500；
 *   本测试直接驱动校验器，保证注解与字段类型匹配（此类错误单元测试层无法发现）。
 * */
@DisplayName("告警模版规则行 DTO 校验回归测试")
class AlarmTemplateItemDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("UpdateDTO 合法载荷可正常校验（不抛 UnexpectedTypeException）")
    void updateDto_shouldValidate_withoutTypeMismatch() {
        AlarmTemplateItemUpdateDTO dto = new AlarmTemplateItemUpdateDTO();
        dto.setRuleName("设备离线");
        dto.setOperator(null);
        dto.setThreshold(new BigDecimal("15"));
        dto.setAlarmLevel(2);
        dto.setEnabled(true);

        Set<ConstraintViolation<AlarmTemplateItemUpdateDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("UpdateDTO 缺少告警级别：报 NotNull 违规而非校验器异常")
    void updateDto_shouldReportNotNullViolation_whenAlarmLevelMissing() {
        AlarmTemplateItemUpdateDTO dto = new AlarmTemplateItemUpdateDTO();
        dto.setRuleName("高温警告");

        Set<ConstraintViolation<AlarmTemplateItemUpdateDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("alarmLevel")
                && v.getConstraintDescriptor().getAnnotation() instanceof NotNull);
    }

    @Test
    @DisplayName("CreateDTO 合法载荷可正常校验")
    void createDto_shouldValidate() {
        AlarmTemplateItemCreateDTO dto = new AlarmTemplateItemCreateDTO();
        dto.setRuleType("METRIC");
        dto.setRuleName("回风高温警告");
        dto.setOperator("GT");
        dto.setThreshold(new BigDecimal("32"));
        dto.setAlarmLevel(2);
        dto.setPointId(32L);

        Set<ConstraintViolation<AlarmTemplateItemCreateDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("注解类型契约：字符串字段用 NotBlank、数值字段用 NotNull")
    void annotationContract_shouldMatchFieldTypes() throws NoSuchFieldException {
        NotBlank ruleNameNotBlank = AlarmTemplateItemUpdateDTO.class
                .getDeclaredField("ruleName").getAnnotation(NotBlank.class);
        assertThat(ruleNameNotBlank).as("ruleName 应为 @NotBlank").isNotNull();

        NotNull alarmLevelNotNull = AlarmTemplateItemUpdateDTO.class
                .getDeclaredField("alarmLevel").getAnnotation(NotNull.class);
        assertThat(alarmLevelNotNull).as("Integer 字段 alarmLevel 应为 @NotNull").isNotNull();
    }
}
