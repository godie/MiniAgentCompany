# MiniAgentCompany

## Description
MiniAgentCompany is a Java-based multi‑model AI pipeline that orchestrates idea generation, debate, synthesis, and planning phases. It provides a modular architecture for building and evaluating AI‑driven ideas and pipelines.

## Requirements
- Java 17+ (or compatible JDK)
- Maven 3.8+
- Git (for version control)
- Internet connection for remote repository access
- Optional: Docker if you prefer containerized builds

## How to Run
```bash
# Clone the repository (if you haven't already)
git clone https://github.com/godie/MiniAgentCompany.git
cd MiniAgentCompany

# Build the project
mvn clean install

# Run the pipeline (example entry point)
java -jar target/miniagentcompany.jar
```

## How to Test
The project includes a comprehensive suite of JUnit tests.
```bash
# Run all tests
mvn test
```

## Future Work
- Add Docker support for easier deployment
- Implement CI/CD pipelines with GitHub Actions
- Expand the multi‑model orchestration to include additional LLM providers
- Improve documentation and add usage examples for each pipeline phase
