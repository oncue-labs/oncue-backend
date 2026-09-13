package com.oncue.conversation.builder;

import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.conversation.model.DialoguePolicy;
import com.oncue.conversation.rules.DialogueRulesMerger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DialoguePolicyBuilder {

    private static final List<String> DEFAULT_STAGES = List.of("greeting", "context", "goal", "closing");

    private final DialogueRulesMerger dialogueRulesMerger;

    public DialoguePolicyBuilder(DialogueRulesMerger dialogueRulesMerger) {
        this.dialogueRulesMerger = dialogueRulesMerger;
    }

    public DialoguePolicy build(
            Persona persona,
            Scenario scenario,
            String scenarioContext,
            String callGoal,
            Locale locale
    ) {
        DialogueRulesMerger.MergedRules mergedRules = dialogueRulesMerger.merge(persona, scenario);

        return new DialoguePolicy(
                role(persona, scenario),
                DEFAULT_STAGES,
                valueOrEmpty(callGoal),
                List.of(),
                DialogueRulesMerger.COMMON_FORBIDDEN_TOPICS,
                DialogueRulesMerger.COMMON_TERMINATION_CONDITIONS,
                language(locale),
                persona == null ? "" : valueOrEmpty(persona.getVoiceId()),
                mergedRules.instructions(),
                mergedRules.dialogueRules(),
                valueOrEmpty(scenarioContext),
                Map.of()
        );
    }

    private static String role(Persona persona, Scenario scenario) {
        if (persona != null && persona.getName() != null && !persona.getName().isBlank()) {
            return persona.getName();
        }
        if (scenario != null && scenario.getName() != null && !scenario.getName().isBlank()) {
            return scenario.getName();
        }
        return "";
    }

    private static String language(Locale locale) {
        return locale != null && "ko".equals(locale.getLanguage()) ? "ko-KR" : "en-US";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
