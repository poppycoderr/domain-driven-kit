<p align="center">
    <img src="./logo.png" alt="ddk logo" width=200 height=200 />
</p>
<h1 align="center">Domain Driven Kit</h1>
<p align="center">
    <em >🧩 — Modularization, layered architecture, and domain modeling in DDD</em>
</p>
<p align="center">
    <em>⚡ — The scaffold efficiently accelerates project construction and enhances development productivity</em>
</p>
<p align="center">
    <em>📦 — Reusable DDD codebase and infrastructure</em>
</p>

<p align="right"><a href="README.md">🇨🇳中文</a></p>

### 🚀 Project Introduction:

`domain-driven-kit` 🛠️ is an open-source scaffolding project built with Java, designed to help developers quickly construct layered applications based on Domain-Driven Design (DDD) principles. **It focuses on addressing the abstraction and implementation challenges of DDD, providing a standard and reusable architectural pattern to help teams unify their development practices.**

### ✨ Key Features:

*   **🧱 Standard DDD 4-Layered Project Structure:** Based on the classic four-layer architecture (User Interface, Application, Domain, and Infrastructure), providing clear responsibility separation and dependency management.
*   **🎈 Lightweight DDD 3-Layered Project Structure:** Retains the user interface layer and infrastructure layer from the four-layer architecture, merging the application layer and domain layer into a business logic layer. This three-layer architecture is better suited for building simple projects.
*   **📦 Pre-configured Spring Boot Starter:** Facilitates the rapid integration of common components, including data access, transaction management, logging, and validation, with default configurations to simplify development setup.
*   **💡 Best Practices DDD Architectural Example:** Offers a user management example showcasing core DDD concepts such as Entities, Value Objects, Aggregates, Domain Services, and Repositories, helping developers quickly understand and apply DDD principles.
*   **⚡ Rapid Prototyping:** Enables the quick generation of a DDD project skeleton for rapid prototype verification.
*   **🤝 Unified Team Standards:** Provides a standard architectural pattern to help teams unify their development standards.
*   **⚙️ Extensibility:** Allows for custom extensions based on specific business needs.

### 🎯 Project Goals:

*   **🌱 Lower the Barrier to DDD Practice:** Empowering DDD beginners to quickly get started and apply it to real-world projects.
*   **🚀 Increase Development Efficiency:** Reduce repetitive development through pre-configurations and code examples, thus enhancing efficiency.
*   **👨‍💻 Unify Team Standards:** Provide a standardized DDD architecture to help teams establish uniform development practices.

### 🤔 Pain Points Addressed:

*   😫  DDD concepts are abstract and difficult to implement?
*   😵‍💫 Layered architecture is cumbersome and requires extensive configuration?
*   🧐  Finding appropriate DDD practical examples is challenging?

### 🛠️ How to Use:

1.  [Provide detailed quick start steps]
2.  [Provide code examples]
3.  [Provide Spring Boot Starter configuration instructions]

### 🏛️ Architectural Example:

* 🧱[4-layer](./ddk-archetypes/ddk-layer4-archetype/README.md)

<p align="center">
    <img src="./ddk-archetypes/ddk-layer4-archetype/4-layer.png" alt="4-layer" width=350/>
</p>

* 🎈[3-layer](./ddk-archetypes/ddk-layer3-archetype/README.md)

<p align="center">
    <img src="./ddk-archetypes/ddk-layer3-archetype/3-layer.png" alt="3-layer" width=340/>
</p>

### 📚 Documentation:

*   [ddk.doc](https://poppycoder.netlify.app)

### 🤝 Contributing:

*   Contributions and feedback are welcome! Feel free to submit PRs and issues.

### 💬 Community:

*   [Link to discussion forum]

### 🗓️ Maintenance Plan:

| Phase                                     | Task                                                                                                                                                                 | Status | Completion Date | Remark |
|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|-----------------|--------|
| Project Initialization (🐣)               | Create basic project structure and Maven configuration 🏗️                                                                                                           | ✅      | 2025-01-09      |        |
|                                           | Introduce code style guidelines, quality checks, and architectural enforcement tools (e.g., Archunit, Checkstyle, PMD, SonarLint) 🎨                                 | ❌      |                 |        |
|                                           | Basic dependencies and tool libraries, unify related dependency versions and structure (e.g., Lombok, MapStruct) 📚                                                  | 🚧     |                 |        |
| Core Module Enhancement (✨)               | Common Response Module: Provide a unified API response format, including status code, message, and data, for easy client processing 📦                               | ✅      | 2025-02-08      |        |
|                                           | Exception Module: Define a unified exception handling mechanism, including custom exception classes, global exception handlers, etc., to improve system robustness ❗ | ✅      | 2025-02-08      |        |
|                                           | Pagination Module: Provide common pagination functionality, supporting various pagination methods to simplify pagination logic development 📄                        | ✅      | 2025-02-10      |        |
|                                           | Mapping Module: Implement automatic object conversion based on MapStruct or other mapping frameworks, reducing the manual writing of mapping code 🗺️                | 🚧     |                 |        |
|                                           | Persistence Module: Encapsulate commonly used database operations, providing a unified data access interface to reduce the complexity of database access 🗄️         | ✅      | 2025-02-11      |        |
| Four-Layer Architecture Development (🏢)  | Adapter Layer: Based on User, implement Web CRUD interfaces 🌐                                                                                                       | 🚧     |                 |        |
|                                           | Application Layer: Implement orchestration calls for the Domain Layer and Infrastructure Layer ⚙️                                                                    | 🚧     |                 |        |
|                                           | Domain Layer: Implement the domain model and best practices for business rules 🧠                                                                                    | 🚧     |                 |        |
|                                           | Infrastructure Layer: Implement data access, caching, message passing, etc. 🛠️                                                                                      | 🚧     |                 |        |
| Three-Layer Architecture Development (🏢) | Simplify based on the four-layer architecture, merging the application layer and domain layer into the business logic layer 🤝                                       | ❌      |                 |        |
| Starter Packaging (📦)                    | Encapsulate Starters to simplify configuration and usage, improving development efficiency ⚡                                                                         | 🚧     |                 |        |
| *ddk-web-starters*                        | Simplify Web configuration, providing common configurations and exception handling, generic returns, etc. 🌐                                                         | 🚧     |                 |        |
| *ddk-archguard-starters*                  | Simplify ArchUnit configuration and integration, predefine layered architecture constraints, easily implement architectural enforcement and compliance checks 🛡️    | ❌      |                 |        |
| *ddk-db-starters*                         | Simplify the configuration and management of multiple data sources 🗄️                                                                                               | ❌      |                 |        |
| *ddk-seata-starters*                      | Simplify the configuration and use of distributed transaction Seata, providing a distributed transaction solution ⚙️                                                 | ❌      |                 |        |
| *ddk-remote-starters*                     | Simplify the configuration and use of remote service calls, integrating Feign, RestTemplate, etc. 📞                                                                 | ❌      |                 |        |
| *ddk-cache-starters*                      | Integrate Redis, Caffeine to implement multi-level caching solutions, providing simplified configuration and use of caching ⚡                                        | ❌      |                 |        |
| *ddk-locker-starters*                     | Provide simplified configuration and use of distributed locks, supporting multiple implementations such as Redis, ZooKeeper, etc. 🔒                                 | ❌      |                 |        |
| *ddk-limiter-starters*                    | Provide simplified configuration and use of rate limiting, supporting multiple algorithms such as counters, token buckets, leaky buckets, etc. 🚦                    | ❌      |                 |        |
| *ddk-mq-starters*                         | Simplify the configuration and use of message queues, supporting multiple message queues such as RabbitMQ, Kafka, etc. ✉️                                            | ❌      |                 |        |
| *ddk-es-starters*                         | Simplify the configuration and use of Elasticsearch clients, providing commonly used query and indexing operations 🔍                                                | ❌      |                 |        |
| *ddk-monitor-starters*                    | Simplify the configuration and integration of monitoring metrics, providing commonly used monitoring panels and alarm rules 📊                                       | ❌      |                 |        |
| *ddk-tracer-starters*                     | Simplify the configuration and integration of trace tracking, supporting mainstream trace tracking systems such as SkyWalking, Jaeger, Zipkin 🔗                     | ❌      |                 |        |
| *ddk-schedule-starters*                   | Simplify the configuration and use of distributed task scheduling, supporting Cron expressions, scheduled task management, etc. ⏰                                    | ❌      |                 |        |
| *ddk-auth-starters*                       | Provide simplified configuration and use of authentication and authorization, supporting multiple authentication methods such as OAuth2, JWT, etc. 🔑                | ❌      |                 |        |
| *ddk-ai-starters*                         | Simplify AI framework configuration, supporting multiple modes such as SpringAI, Langchain, etc. 🤖                                                                  | ❌      |                 |        |
|                                           | ... to be continue                                                                                                                                                   | ❌      |                 |        |
| Continuous Refactoring (♻️)               | Optimize code structure and performance based on best practices 👓                                                                                                   | 🔄     |                 |        |
|                                           | Improve test coverage to ensure code quality ✅                                                                                                                       | 🔄     |                 |        |
|                                           | Improve project documentation to ensure developers better understand the project 📜                                                                                  | 🔄     |                 |        |
|                                           | Continuously optimize project dependencies and keep them up to date ⬆️                                                                                               | 🔄     |                 |        |
|                                           | Continuously explore and integrate excellent and commonly used components in the Spring Boot ecosystem 🚀                                                            | 🔄     |                 |        |

**Status Description:**

*   ✅: Completed
*   🚧: In Development
*   ❌: Not Started
*   🔄: Ongoing

`domain-driven-kit` is committed to lowering the learning curve and implementation challenges of DDD, allowing developers to focus more on business logic and improve development efficiency. 🚀