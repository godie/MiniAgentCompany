package com.ideapipeline.orchestrator;

import com.ideapipeline.client.JsonParser;
import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GatekeeperOrchestrator {

    private static final String REFINEMENT_QUESTIONS_PROMPT = """
        You are a product strategist expert at turning vague ideas into clear specs.
        Your job is to ask the MINIMUM and MOST IMPORTANT questions to understand an idea.
        Maximum 6 questions. Each question must disambiguate something critical.
        Respond ONLY in JSON with this exact format, no preamble, no markdown:
        {"questions": ["question 1", "question 2", ...]}
        """;

    private static final String CONTEXT_ENRICHMENT_PROMPT = """
        You are a product strategist. Given an idea and refinement answers,
        generate a concise executive summary (max 200 words) that captures:
        - The core essence of the idea
        - The problem it solves
        - The target user
        - Key constraints and assumptions
        Respond with plain text only, no JSON, no markdown.
        """;

    private final LlmClient llmClient;

    public GatekeeperOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<String> generateRefinementQuestions(RawIdea idea) {
        log.info("Generating refinement questions for idea (length: {} characters).", 
                idea.description().length());

        if (idea.description() == null || idea.description().isBlank()) {
            throw new PipelineException("Empty idea description", "Phase0", 0);
        }

        try {
            String response = llmClient.chat(REFINEMENT_QUESTIONS_PROMPT, idea.description());
            if (response == null || response.isBlank()) {
                throw new PipelineException("Empty response from LLM", "Phase0", 0);
            }

            JsonNode jsonNode = JsonParser.parse(response);
            JsonNode questionsNode = jsonNode.get("questions");
            
            if (questionsNode != null && questionsNode.isArray()) {
                List<String> questions = new ArrayList<>();
                for (JsonNode questionNode : questionsNode) {
                    questions.add(questionNode.asText());
                }
                log.info("Generated {} refinement questions.", questions.size());
                return questions;
            } else {
                throw new PipelineException("Invalid questions format in LLM response", "Phase0", 0);
            }
        } catch (PipelineException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to generate refinement questions", e);
            throw new PipelineException("Failed to parse questions JSON", "Phase0", 0, e);
        }
    }

    public IdeaContext buildContext(RawIdea rawIdea, List<RefinementQA> answers) {
        log.info("Building context for idea (length: {} characters) with {} answers.", 
                rawIdea.description().length(), answers.size());

        try {
            String qaBlock = formatQABlock(answers);
            String userMessage = String.format("""
                Original idea: %s

                Refinement answers:
                %s
                """, rawIdea.description(), qaBlock);

            log.debug("Building context with message: {}", userMessage);

            String response = llmClient.chat(CONTEXT_ENRICHMENT_PROMPT, userMessage);
            if (response == null || response.isBlank()) {
                throw new PipelineException("Empty response from LLM", "Phase0", 0);
            }

            return new IdeaContext(
                    rawIdea.description(),
                    answers,
                    response.trim()
            );
        } catch (PipelineException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to build enriched context", e);
            throw new PipelineException("Failed to build context", "Phase0", 0, e);
        }
    }

    private String formatQABlock(List<RefinementQA> answers) {
        if (answers.isEmpty()) {
            return "No refinement answers provided";
        }
        
        return answers.stream()
                .map(qa -> String.format("Q: %s\nA: %s", qa.question(), qa.answer()))
                .collect(Collectors.joining("\n\n"));
    }
}
