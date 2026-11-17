# MiniAgentCompany

## Descripción
MiniAgentCompany es un pipeline de IA multimodelo basado en Java que orquesta las fases de generación de ideas, debate, síntesis y planificación. Proporciona una arquitectura modular para construir y evaluar ideas y pipelines impulsados por IA.

## Requisitos
- Java 17+ (o un JDK compatible)
- Maven 3.8+
- Git (para el control de versiones)
- Conexión a Internet para acceder a repositorios remotos
- Opcional: Docker si prefieres compilaciones en contenedores

## Cómo ejecutar
```bash
# Clonar el repositorio (si aún no lo has hecho)
git clone https://github.com/godie/MiniAgentCompany.git
cd MiniAgentCompany

# Compilar el proyecto
mvn clean install

# Ejecutar el pipeline (punto de entrada de ejemplo)
java -jar target/miniagentcompany.jar
```

## Cómo probar
El proyecto incluye una suite completa de tests JUnit.
```bash
# Ejecutar todos los tests
mvn test
```

## Trabajo futuro
- Añadir soporte de Docker para facilitar el despliegue
- Implementar pipelines de CI/CD con GitHub Actions
- Ampliar la orquestación multimodelo para incluir proveedores de LLM adicionales
- Mejorar la documentación y añadir ejemplos de uso para cada fase del pipeline
