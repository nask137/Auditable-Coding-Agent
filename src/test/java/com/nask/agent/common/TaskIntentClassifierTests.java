package com.nask.agent.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskIntentClassifierTests {
    @Test
    void selectsTestWorkflowForValidationOnlyCompileRequest() {
        assertThat(TaskIntentClassifier.defaultWorkflowFor("coding-agent", "测试一下是否能正常编译"))
                .isEqualTo("test-agent");
    }

    @Test
    void keepsCodingWorkflowForMutationRequestThatMentionsTests() {
        assertThat(TaskIntentClassifier.defaultWorkflowFor("coding-agent", "修复测试失败的问题"))
                .isEqualTo("coding-agent");
    }

    @Test
    void preservesExplicitNonDefaultWorkflow() {
        assertThat(TaskIntentClassifier.defaultWorkflowFor("review-agent", "测试一下是否能正常编译"))
                .isEqualTo("review-agent");
    }
}
