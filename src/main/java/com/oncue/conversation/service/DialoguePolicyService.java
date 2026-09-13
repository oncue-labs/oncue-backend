package com.oncue.conversation.service;

import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.conversation.builder.DialoguePolicyBuilder;
import com.oncue.conversation.model.DialoguePolicy;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DialoguePolicyService {

    private final DialoguePolicyBuilder dialoguePolicyBuilder;

    public DialoguePolicyService(DialoguePolicyBuilder dialoguePolicyBuilder) {
        this.dialoguePolicyBuilder = dialoguePolicyBuilder;
    }

    public DialoguePolicy build(
            Persona persona,
            Scenario scenario,
            String scenarioContext,
            String callGoal,
            Locale locale
    ) {
        return dialoguePolicyBuilder.build(persona, scenario, scenarioContext, callGoal, locale);
    }
}
