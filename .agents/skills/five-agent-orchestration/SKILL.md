---
name: five-agent-orchestration
description: Teaches agents how to spawn and manage the persistent 5-agent team architecture
---

# Five-Agent Orchestration Model

## Philosophy
The core philosophy of this project is to avoid spawning ephemeral, "1-use" throwaway agents. 
Instead, we maintain a persistent, stateful team of exactly **five specialized agents** that lie dormant (idle) in memory. They receive recursive updates and perform targeted passes over the codebase. 

Crucially, **the User is the Central Orchestrator**. The agents must NEVER apply sweeping changes blindly. They must draft proposals, report them to the main agent, and wait for the user's explicit authorization.

## The 5 Persistent Roles
When asked to initialize the team, spawn these exact 5 agents concurrently using `invoke_subagent`:

1. **String Extractor** (`TypeName: research`): Finds hardcoded user-facing UI strings (like `JLabel` or `JOptionPane`) and abstracts them into the JSON-based `StringManager`.
2. **Switch Refactorer** (`TypeName: self`): Converts deep `if..else if` chains evaluating identical variables (Strings/Enums/ints) into modern Java 14+ `switch` expressions.
3. **Loop Optimizer** (`TypeName: self`): Modernizes clunky loops (e.g. traditional indexed `for` loops) into enhanced `for-each` or Streams, BUT strictly avoids over-complicating simple standard iteration.
4. **Duplicate Logic Finder** (`TypeName: self`): Scans for duplicate code blocks (UI layouts, algorithms) and abstracts them into reusable helper methods or abstract base classes (e.g. `AbstractSkinOverridesPanel`, `AbstractCenterPanel`, `AbstractWingListPane`, `AbstractQAReportDialog`, `SaveActionUtilities`, `PolygonSimplifier`, `SpriteContourTracer`, `DialogHelper`).
5. **Robustness Enhancer** (`TypeName: self`): Hunts for naked I/O or JSON parsers, unprotected exceptions, or swallowed stack-traces, wrapping them in safe `try-catch` blocks utilizing `@Log4j2` and `log.trace` breakpoints.

## Skill Resources & Tooling
- [base_agent_spec.json](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/five-agent-orchestration/examples/base_agent_spec.json): Reference subagent invocation payload for initializing the 5 persistent tasks.
- [shipmaker_tools.py](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/scripts/shipmaker_tools.py): CLI helper for duplicate code scanning (`find-dupes`), coding standard checks (`check-standards`), regex patching (`patch`), and build verification (`verify`).

## Lifecycle Management
- **Wake Up**: Use `send_message` to ping the dormant agents with a recursive update mandate.
- **Reporting**: Agents respond with a brief target summary (Files affected, Intended Changes).
- **Approval**: Wait for the User to type "go" or "approve".
- **Execution**: Pass the approval down to the specific agent.
- **Verification**: Ensure agents run `mvn package -DskipTests` before reporting success and returning to sleep.
