# DDK ArchGuard Starter

This starter helps integrate ArchUnit into your DDK project for architecture governance.
It provides core ArchUnit dependencies and a set of predefined architectural rules.

## Features

- Provides `archunit-junit5-api` and `archunit-junit5-engine` dependencies.
- Includes `CommonArchRules.java` with a predefined layered architecture rule (`CommonArchRules.LAYERED_ARCHITECTURE_RULE`).

## Prerequisites

Your project should be set up to use JUnit 5 for ArchUnit tests.

## How to Use

1.  **Include the starter in your project's `pom.xml`:**

    ```xml
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-archguard-starter</artifactId>
        <version>${ddk.version}</version> <!-- Ensure this matches your project's ddk version -->
        <scope>test</scope> <!-- Typically, architecture tests are in test scope -->
    </dependency>
    ```

2.  **Create an ArchUnit test class:**

    In your test sources (e.g., `src/test/java/com/example/architecture/ArchitectureTest.java`):

    ```java
    import com.tngtech.archunit.core.importer.ClassFileImporter;
    import com.tngtech.archunit.core.domain.JavaClasses;
    import com.tngtech.archunit.junit.AnalyzeClasses;
    import com.tngtech.archunit.junit.ArchTest;
    import com.tngtech.archunit.lang.ArchRule;
    import static com.ddk.archguard.starter.rules.CommonArchRules.LAYERED_ARCHITECTURE_RULE;
    // If you have your own rules or want to customize:
    // import static com.tngtech.archunit.library.Architectures.layeredArchitecture;


    // Specify the packages to analyze
    @AnalyzeClasses(packages = "com.example.yourproject")
    public class ArchitectureTest {

        @ArchTest
        public static final ArchRule checkLayeredArchitecture = LAYERED_ARCHITECTURE_RULE;

        // Example of a custom rule if needed:
        // @ArchTest
        // public static final ArchRule myCustomRule = layeredArchitecture()
        //     .consideringAllDependencies()
        //     .layer("MyService").definedBy("..service..")
        //     .layer("MyRepository").definedBy("..repository..")
        //     .whereLayer("MyService").mayOnlyAccessLayers("MyRepository");
    }
    ```

3.  **Customize Package Identifiers in Rules (Important):**

    The predefined `LAYERED_ARCHITECTURE_RULE` in `CommonArchRules` uses generic package identifiers like `"..ui.."` or `"..application.."`. You will likely need to adjust these to match your project's actual package structure.

    To do this, you can either:
    a. Copy the rule definition from `CommonArchRules.java` into your `ArchitectureTest.java` and modify the `definedBy(...)` parts.
    b. (Advanced) If the starter evolves, it might provide a way to configure these package names.

## Provided Rules

-   **`CommonArchRules.LAYERED_ARCHITECTURE_RULE`**:
    Checks for adherence to a typical layered architecture:
    - UI layer (`..ui..`, `..adapter..`)
    - Application layer (`..application..`)
    - Domain layer (`..domain..`)
    - Infrastructure layer (`..infrastructure..`)

    Access permissions:
    - UI: Should not be accessed by other layers.
    - Application: May only be accessed by UI.
    - Domain: May only be accessed by Application and Infrastructure.
    - Infrastructure: May only be accessed by Application and Domain.

## Further Customization

You can define your own ArchUnit rules alongside or instead of the provided ones. Refer to the [ArchUnit documentation](https://www.archunit.org/userguide/html/000_Index.html) for more details on writing rules.
