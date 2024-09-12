# Pitest Gradle Plugin

This is a Gradle plugin for running Pitest mutation testing. It is designed to be used in environments where jdk and junit are of old version, and you have no network access to download the Pitest JARs.

## Features

- Compatibility with Java 8 and later
- Integration with Jacoco for code coverage analysis
- Support for running tests in parallel
- Support to run pitest for passed class only

## Setup

1. Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'com.github.jaksonlin.pitest' version '1.0.0'
}
```

2. Configure Jacoco in your `build.gradle`:

```groovy

jacoco {
    toolVersion = "0.8.7" // Use the latest version
}

jacocoTestReport {
    reports {
        xml.enabled true
        html.enabled true
    }
}
```

3. Add Pitest dependencies:

- Download the Pitest JARs manually (pitest-1.16.0.jar, pitest-entry-1.16.0.jar, pitest-commandline-1.16.0.jar)
- Place them in a directory within your project (e.g., `libs/`)
- Add the following to your `build.gradle`:

```groovy
dependencies {
    testImplementation files('libs/pitest-1.16.0.jar', 'libs/pitest-entry-1.16.0.jar', 'libs/pitest-commandline-1.16.0.jar')
}
```

4. Configure the Pitest plugin:

```groovy
pitest {
    pitestVersion = '1.16.0'
    mutators = ['STRONGER', 'DEFAULTS']
    threads = 4
    timeoutConst = 5000
    jacocoReportPath = "$project.buildDir/../../target/jacoco/jacocoTest.exec"
}
```


5. Ensure your test classes follow the naming convention: `Test{ClassName}` or `{ClassName}Test`.

## Usage

Run the mutation tests:

``` bash
./gradlew mutest
```


This will:
1. Run your tests
2. Generate Jacoco coverage reports
3. Run Pitest for each passing test class
4. Generate Pitest reports in `build/reports/pitest/`

## Notes

- The plugin automatically sets up the classpath for Pitest.
- Jacoco reports are appended to `target/jacoco/jacocoTest.exec`.
- Pitest reports are generated in XML format.
- Failed tests are skipped for Pitest analysis.


