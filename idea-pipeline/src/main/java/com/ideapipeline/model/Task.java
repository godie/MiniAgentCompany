package com.ideapipeline.model;

public record Task(
        String id,
        String title,
        String description,
        String epicId,
        String userStory
) {}
