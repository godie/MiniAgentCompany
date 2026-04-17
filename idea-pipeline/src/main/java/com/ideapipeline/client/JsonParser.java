package com.ideapipeline.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class to parse LLM responses, specifically designed to strip markdown formatting.
 * This utility should be package-private and used by all orchestrators expecting JSON output.
 */
public class JsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses an LLM response string into a JsonNode, stripping potential markdown fences (```json ... ```).
     * @param response The raw string response from the LLM.
     * @return JsonNode representing the parsed JSON object.
     */
    public static JsonNode parse(String response) {
        String clean = response
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("```", "")
                .trim();
        try {
            return MAPPER.readTree(clean);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response: " + clean, e);
        }
    }
}