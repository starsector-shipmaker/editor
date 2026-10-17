---
name: coding-standards
description: Strict coding standards, refactoring guidelines, and integrity rules for the Starsector Ship Editor
---

# Coding Standards & Integrity Rules

When working on the Starsector Ship Editor, agents must strictly follow these coding standards:

## 1. Zero Open-Source Copying
Do NOT copy logic, snippets, or core functionality from external open-source projects. All code must be written internally or rely strictly on the existing `.agents/rules` and `.agents/skills` documentation.

## 2. Iteration & Loop Simplicity
Keep simple things simple. Maintain standard `if-else` blocks for simple iterating operations. Do NOT over-complicate basic loops with forced Java 8 Streams, confusing ternary operators, or abstract design patterns. 

## 3. Modern Java Switches
When refactoring deep or repetitive `if..else if` chains that evaluate the same variable (such as Enums, Strings, or ints), use clean, modern Java 14+ `switch` expressions (e.g., `case X -> { ... }`).

## 4. Robustness & Exception Handling
- Always wrap risky operations (e.g., file I/O, JSON/CSV parsing, complex math transformations) in secure `try-catch` blocks.
- Exceptions must be logged gracefully using Log4j2 (`log.error("...", e)`). Do not silently swallow exceptions, and never use `System.out.println` in production classes.

## 5. Trace "Breakpoints"
Because standard Java lacks inline debugger breakpoints (`debugger;`), you must implement trace points by inserting highly descriptive `log.debug(...)`, `log.trace(...)`, or `assert` statements directly before executing complex logic blocks to aid in runtime debugging.

## 6. Naming Conventions (Checkstyle Enforced)
- **Classes/Interfaces**: `PascalCase` (e.g., `ShipPainter`).
- **Methods**: `camelCase` (e.g., `rotatePointByCenter`).
- **Instance Fields/Local Variables**: `camelCase`. No Hungarian notation (do NOT prefix with `m` or `_`).
- **Constants (`static final`)**: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_ZOOM_LEVEL`), with the exception of the standard `log` instance.
*Any violations of these rules will actively fail the `mvn package` checkstyle validation build phase.*
