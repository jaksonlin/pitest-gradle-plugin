package com.github.jaksonlin.pitest

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

class PitestPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Create the extension
        def extension = project.extensions.create('pitestConfig', PitestExtension)

        // Apply the Java plugin if not already applied
        project.plugins.apply('java')

        project.task('mutest', type: Test) {
            doFirst {
                maxParallelForks = 1
            }
            testLogging {
                println("test start")
            }
            ignoreFailures = true
            jacoco {
                append = true
                destinationFile = project.file(extension.jacocoReportPath)
            }
            reports {
                junitXml.enabled = true
            }

            // Store test results in a shared map
            def testResults = [:]
            beforeTest { descriptor ->
                testResults[descriptor.getClassName()] = true
            }
            afterTest { descriptor, result ->
                if (result.getResultType() == TestResult.ResultType.FAILURE) {
                    testResults[descriptor.getClassName()] = false
                }
            }

            doLast {
                def pitestError = [:]
                testResults.each { testClassName, passed ->
                    if (passed) {
                        println "trying pitest for $testClassName"
                        pitestError[testClassName] = true
                        if(!runPitestForClass(project, testClassName, extension)){
                            pitestError[testClassName] = false
                        }
                    }
                }
                def classpathFile = project.file('pitest-classpath.txt')
                if (classpathFile.exists()) {
                    classpathFile.delete()
                }
                println "Number of problematic test class: " + pitestError.size()
            }
        }
    }

    private boolean runPitestForClass(Project project, String testClassName, PitestExtension extension) {
        try {
            project.task("pitest-$testClassName", type: org.gradle.api.tasks.Exec) {
                workingDir project.projectDir

                // Utilize Gradle's classpath handling
                def classpath = project.sourceSets.test.runtimeClasspath + project.sourceSets.main.runtimeClasspath
                inputs.files classpath

                // Create classpath file
                def classpathFile = project.file('pitest-classpath.txt')
                classpathFile.withWriter { writer ->
                    classpath.each { entry ->
                        writer.writeLine entry.absolutePath
                    }
                }

                // Dynamically gather Pitest JARs
                def pitestJars = project.configurations.testRuntimeClasspath.filter { it.name.startsWith("pitest-") }
                def pitestCp = pitestJars.collect { it.absolutePath }.join(File.pathSeparator)

                // Concisely build sourceDirs
                def sourceDirs = project.sourceSets.main.allJava.srcDirs.join(",")

                // Set the target class for mutation test
                def targetClass = testClassName.replaceAll(/^Test|Test$/, "")
                println "Run PITest mutation testing $targetClass , using $testClassName"

                // Set the report dir
                def reportDir = project.file("build/reports/pitest/$targetClass")

                // Set command and options using classpath file
                commandLine 'java', '-cp', pitestCp, 'org.pitest.mutationtest.commandline.MutationCoverageReport',
                    '--reportDir', reportDir,
                    '--sourceDirs', sourceDirs,
                    '--targetClasses', targetClass,  
                    '--targetTests',   testClassName,    
                    '--classPathFile', classpathFile.absolutePath,
                    '--outputFormats', extension.outputFormats.join(','),
                    '--threads', extension.threads,
                    '--timeoutConst', extension.timeoutConst,
                    '--mutators', extension.mutators.join(','),
                    '--verbose'
            }

            project.tasks["pitest-$testClassName"].execute()
            return true
        } catch (Exception ex) {
            println "Error running Pitest for $testClassName: ${ex.message}"
            return false
        }
    }
}