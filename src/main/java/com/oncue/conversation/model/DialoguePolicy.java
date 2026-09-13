package com.oncue.conversation.model;

import java.util.List;
import java.util.Map;

public record DialoguePolicy(
        String role,
        List<String> stages,
        String goal,
        List<String> allowedTopics,
        List<String> forbiddenTopics,
        List<String> terminationConditions,
        String language,
        String voiceId,
        List<String> instructions,
        List<String> dialogueRules,
        String scenarioContext,
        Map<String, Object> voiceSettings
) {

    public DialoguePolicy {
        role = valueOrEmpty(role);
        stages = immutableList(stages);
        goal = valueOrEmpty(goal);
        allowedTopics = immutableList(allowedTopics);
        forbiddenTopics = immutableList(forbiddenTopics);
        terminationConditions = immutableList(terminationConditions);
        language = valueOrEmpty(language);
        voiceId = valueOrEmpty(voiceId);
        instructions = immutableList(instructions);
        dialogueRules = immutableList(dialogueRules);
        scenarioContext = valueOrEmpty(scenarioContext);
        voiceSettings = voiceSettings == null ? Map.of() : Map.copyOf(voiceSettings);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<String> immutableList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
