# Pitest Gradle Plugin

This is a Gradle plugin for running Pitest mutation testing. It is designed to be used in environments where jdk and junit are of old version, and you have no network access to download the Pitest JARs.

## Features

- Compatibility with Java 8 and later
- Integration with Jacoco for code coverage analysis
- Support for running tests in parallel
- Support to run pitest for passed class only
- Support to add additional required jars
- Support to change pitest jar by specifying the directory
- Support running pitest on not green test suite. for a test suite contains dozens of failure test classes, we can still evaluate the mutation score for the passed test classes. This is achieved by matching the test class name with the test cases in the Junit event hook, <ClassName>Test or Test<ClassName>, if the naming convetion is not of this format, you can specify the test cases for the test class in the `includedTestNGGroups` or `excludedTestNGGroups`

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

- Or you can specify the directory of Pitest jars:

```groovy
pitestConfig {
    pitestJarsDirectory = 'libs'
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
    additionalRequiredJars = ['commons-text', 'commons-lang3', 'byte-buddy-agent']
    additionalJarDirectories = ['libs']
    pitestJarsDirectory = '<path to pitest jars>'
}
```

for additionalRequiredJars, you can specify the jars that are not in the Pitest distribution package, for example, you can add dependencies that are not in the classpath when running pitest.

In addition, in order to generate XML format report, `commons-text` and `commons-lang3` are required, and you should keep them in `additionalRequiredJars`, and the plugin will lookup these jars in the `additionalJarDirectories`.

As for `byte-buddy-agent`, it is to address the issue that mockito may fail to initialize inline mock maker in some old version of JDK, and to resolve the issue, we need to load the `byte-buddy-agent` in Pitest as java agent, so that when a new JVM is started by Pitest, the inline mock maker can be initialized. You can remove it if you don't need to use mockito inline mock maker.

To make the test execution stable, we also add the `byte-buddy-agent` for the test execution environment.


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
- The plugin will only run pitest on test classes that the test cases for them are passed.


```groovy
buildscript {
    repositories {
        maven { url "http://cicd.kingdee.com:30081/repository/maven-public/" }
        maven { url "http://172.19.77.89:8080/maven/" }
    }
    dependencies {
        classpath "com.github.jaksonlin:pitest-gradle-plugin:1.0.0"
    }
}
```