package com.nask.agent.common;

import java.util.Locale;

/**
 * Lightweight intent heuristics used before a full model understanding exists.
 */
public final class TaskIntentClassifier {
    private TaskIntentClassifier() {
    }

    public static String defaultWorkflowFor(String configuredWorkflow, String userRequest) {
        var workflow = configuredWorkflow == null || configuredWorkflow.isBlank() ? "coding-agent" : configuredWorkflow;
        if ("coding-agent".equals(workflow) && looksLikeValidationOnly(userRequest)) {
            return "test-agent";
        }
        return workflow;
    }

    public static boolean looksLikeValidationOnly(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            return false;
        }
        var text = userRequest.toLowerCase(Locale.ROOT);
        if (looksLikeMutation(text)) {
            return false;
        }
        return text.contains("compile")
                || text.contains("build project")
                || text.contains("mvn package")
                || text.contains("run test")
                || text.contains("run tests")
                || text.contains("mvn test")
                || text.contains("mvn compile")
                || text.contains("能否编译")
                || text.contains("正常编译")
                || text.contains("编译")
                || text.contains("测试一下")
                || text.contains("测试项目")
                || text.contains("跑测试")
                || text.contains("运行测试")
                || text.contains("验证");
    }

    private static boolean looksLikeMutation(String text) {
        return text.contains("fix")
                || text.contains("repair")
                || text.contains("change")
                || text.contains("modify")
                || text.contains("implement")
                || text.contains("add ")
                || text.contains("修复")
                || text.contains("修改")
                || text.contains("实现")
                || text.contains("新增");
    }
}
