---
name: setup
description: Instructions and quirks for setting up the Starsector Ship Editor development environment
---

# Development Environment Setup Rules

When an agent needs to build, compile, or set up the Starsector Ship Editor project, they MUST follow these rules:

## 1. Java Version Requirement
- **JDK 17 is strictly required** (JDK 17–24 enforced via `maven-enforcer-plugin` `[17,25)`).
- **Do not use Java 25 or newer.** The Lombok library (`1.18.46`) used in this project crashes on JDK 25+ with a `TypeTag :: UNKNOWN ExceptionInInitializerError`.

## 2. Linux (Fedora) Maven Quirks
- On Fedora, the system `mvn` wrapper ignores the `update-alternatives` system and defaults to the system's latest Java (often Java 25).
- **Fix:** You must force Maven to use JDK 17 by setting `JAVA_HOME`. For agents or users running `mvn` commands locally on Linux, create or update `~/.mavenrc` with the path to a local JDK 17 installation.
  - Example: `export JAVA_HOME=$HOME/.jdk/jdk-17.0.20+8`

## 3. Ubuntu/Debian IDE Quirks
- On Ubuntu/Debian, internal IDE terminals (like VS Code) may completely ignore `~/.mavenrc`.
- **Fix:** The `JAVA_HOME` must be explicitly exported in `~/.bashrc` (e.g., `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`) to prevent the environment from defaulting to the wrong Java version and crashing during compilation.

## 4. Concurrent Maven Executions & Packaging
- Always use `mvn package -DskipTests` for build verification so shader resources, Checkstyle validation, and shaded JAR assembly are verified.
- When running `mvn clean compile exec:java`, **never** execute concurrent background `mvn clean` tasks.
- Maven's `exec:java` loads classes dynamically. A concurrent `clean` goal will purge the `target/classes` directory right as the application JVM initializes, throwing a `java.lang.NoClassDefFoundError` and crashing the startup sequence.

## 5. Memory Allocation for Maven
- The `Main.java` class includes a `checkAndRelaunch` method that forks a new JVM with `-Xmx4g` if the available heap is less than ~4GB (`3900 MB`).
- When launching via `mvn exec:java`, the classpath only contains the Plexus Launcher. Therefore, Maven itself must be allocated sufficient memory to avoid triggering this relaunch fallback, which would fail to find the project classpath.
- **Fix:** Use `mvn exec:exec` (which forks a properly configured JVM with `-Xmx4g`) or provide Maven with enough heap memory: `MAVEN_OPTS="-Xmx4g" mvn compile exec:java -Dexec.mainClass="shipeditor.Main"`
