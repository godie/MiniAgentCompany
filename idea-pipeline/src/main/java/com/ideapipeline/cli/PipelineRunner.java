package com.ideapipeline.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.ideapipeline.orchestrator.GatekeeperOrchestrator;
import com.ideapipeline.pipeline.IdeaPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
@Slf4j
public class PipelineRunner implements ApplicationRunner {

    private final IdeaPipeline ideaPipeline;
    private final GatekeeperOrchestrator gatekeeperOrchestrator;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_OUTPUT = "pipeline-result.json";

    public PipelineRunner(IdeaPipeline ideaPipeline, GatekeeperOrchestrator gatekeeperOrchestrator, ObjectMapper objectMapper) {
        this.ideaPipeline = ideaPipeline;
        this.gatekeeperOrchestrator = gatekeeperOrchestrator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String ideaText = getOptionValue(args, "idea");
            if (ideaText == null || ideaText.isBlank()) {
                exitWithError("Error: --idea flag is required");
                return;
            }

            String teamPath = getOptionValue(args, "team");
            if (teamPath == null) {
                exitWithError("Error: --team flag is required");
                return;
            }

            Team team = parseTeam(teamPath);

            boolean interactive = args.containsOption("interactive");
            String outputPath = getOptionValue(args, "output");
            if (outputPath == null) {
                outputPath = DEFAULT_OUTPUT;
            }

            List<RefinementQA> refinements;

            if (interactive) {
                log.info("Running pipeline in interactive mode (idea length={}, team={})", ideaText.length(), teamPath);
                List<String> questions = gatekeeperOrchestrator.generateRefinementQuestions(new RawIdea(ideaText));
                refinements = readAnswersFromConsole(questions);
            } else {
                String refinementsPath = getOptionValue(args, "refinements");
                if (refinementsPath != null) {
                    refinements = parseRefinements(refinementsPath);
                } else {
                    refinements = Collections.emptyList();
                }
                log.info("Running pipeline in direct mode (idea length={}, team={})", ideaText.length(), teamPath);
            }

            PipelineResult result = ideaPipeline.run(new RawIdea(ideaText), refinements, team);
            log.info("Pipeline complete: loopsRequired={}, totalPoints={}", result.loopsRequired(), result.taskGraph().totalPoints());

            writeResult(result, outputPath);
            printSummary(result, outputPath);
        } catch (PipelineException e) {
            log.error("Pipeline error", e);
            exitWithError("Pipeline error [" + e.getPhase() + "]: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error", e);
            exitWithError("Unexpected error: " + e.getMessage());
        }
    }

    Team parseTeam(String path) {
        try {
            return objectMapper.readValue(new File(path), Team.class);
        } catch (Exception e) {
            exitWithError("Error: cannot read team file: " + path);
            return null;
        }
    }

    List<RefinementQA> parseRefinements(String path) {
        try {
            return objectMapper.readValue(new File(path), new TypeReference<List<RefinementQA>>() {});
        } catch (Exception e) {
            exitWithError("Error: cannot read refinements file: " + path);
            return null;
        }
    }

    List<RefinementQA> readAnswersFromConsole(List<String> questions) {
        List<RefinementQA> answers = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        for (int i = 0; i < questions.size(); i++) {
            System.out.println("[" + (i + 1) + "/" + questions.size() + "] " + questions.get(i));
            System.out.print("> ");
            String answer = scanner.nextLine();
            if (answer == null || answer.isBlank()) {
                System.err.println("Warning: empty answer for question " + (i + 1));
            }
            answers.add(new RefinementQA(questions.get(i), answer != null ? answer : ""));
        }
        scanner.close();
        return answers;
    }

    void writeResult(PipelineResult result, String outputPath) {
        try {
            Path parentDir = Path.of(outputPath).getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            Files.writeString(Path.of(outputPath), json);
        } catch (Exception e) {
            exitWithError("Error: cannot write result to " + outputPath);
        }
    }

    void printSummary(PipelineResult result, String outputPath) {
        System.out.println("=== PIPELINE COMPLETE ===");
        System.out.println("Loops required : " + result.loopsRequired());
        System.out.println("Total points   : " + result.taskGraph().totalPoints());
        System.out.println("Tasks estimated: " + result.taskGraph().tasks().size());
        System.out.println("Stack          : " + result.architecture().stack());
        System.out.println("Output file    : " + outputPath);
    }

    private String getOptionValue(ApplicationArguments args, String key) {
        if (!args.containsOption(key)) {
            return null;
        }
        List<String> values = args.getOptionValues(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    void exitWithError(String message) {
        System.err.println(message);
        System.exit(1);
    }
}