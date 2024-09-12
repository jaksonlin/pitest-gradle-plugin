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
                        println "Running pitest for $testClassName"
                        if(!runPitestForClass(project, testClassName, extension)){
                            pitestError[testClassName] = true
                            if (extension.tuningMode) {
                                return // This will break out of the each loop if in tuning mode
                            }
                        }
                    }
                }
                if (!pitestErrors.isEmpty()) {
                    def errorMessage = "Pitest failed for ${pitestErrors.size()} classes. Check logs for details."
                    project.logger.error(errorMessage)
                    if (extension.tuningMode) {
                        throw new Exception(errorMessage)
                    }
                }
            }
        }
    }

    private boolean runPitestForClass(Project project, String testClassName, PitestExtension extension) {
        try {
            def taskName = "pitest-$testClassName"
            project.task(taskName, type: org.gradle.api.tasks.Exec) {
                def logFiles = createLogFiles(project, testClassName)
                workingDir project.projectDir

                def classpath = buildClasspath(project, extension)
                def classpathFile = createClasspathFile(project, testClassName, classpath)
                def pitestCp = getPitestClasspath(project, extension)
                def sourceDirs = getSourceDirs(project)
                def targetClass = getTargetClass(testClassName)
                def reportDir = getReportDir(project, targetClass)

                def pitestCommand = buildPitestCommand(extension, reportDir, sourceDirs, targetClass, testClassName, classpathFile, pitestCp)
                
                logCommand(logFiles.commandLog, pitestCommand)
                def success = executePitestCommand(pitestCommand, logFiles)
                if (!success) {
                    project.logger.error("Pitest failed for $testClassName")
                    def rerunCommand = pitestCommand.join(' ')
                    project.logger.error("Command to rerun Pitest:")
                    project.logger.error(rerunCommand)
                    
                    // Append rerun command to the command log file
                    logFiles.commandLog.append("\n\nRerun command:\n${rerunCommand}")
                    
                    if (extension.tuningMode) {
                        def missingClasses = findMissingClasses(logFiles.errorLog)
                        if (missingClasses) {
                            project.logger.error("Missing class files detected:")
                            missingClasses.each { project.logger.error(it) }
                            
                            // Append missing classes to the command log file
                            logFiles.commandLog.append("\n\nMissing class files:\n${missingClasses.join('\n')}")
                        }
                    }
                    return false
                }

                return true
            }

            project.tasks[taskName].execute()
            return true
        } catch (Exception ex) {
            project.logger.error("Error running Pitest for $testClassName: ${ex.message}")
            ex.printStackTrace()
            return false
        }
    }

    private Map createLogFiles(Project project, String testClassName) {
        return [
            classpath: project.file("pitest-classpath-${testClassName}.txt"),
            commandLog: project.file("pitest-command-${testClassName}.log"),
            outputLog: project.file("pitest-output-${testClassName}.log"),
            errorLog: project.file("pitest-error-${testClassName}.log")
        ]
    }

    private Set<File> buildClasspath(Project project, PitestExtension extension) {
        def classpath = new HashSet<File>()
    
        // Add test and main runtime classpaths
        classpath.addAll(project.sourceSets.test.runtimeClasspath.files)
        classpath.addAll(project.sourceSets.main.runtimeClasspath.files)
        
        // Add additional classpath elements
        extension.additionalClasspathElements.each { element ->
            classpath.addAll(project.files(element).files)
        }
        
        // Add JARs from additional directories
        extension.additionalJarDirectories.each { dirPath ->
            addJarsFromDirectory(project, classpath, dirPath)
        }
        
        return classpath
    }

    private void addJarsFromDirectory(Project project, Set<File> classpath, String dirPath) {
        def dir = new File(dirPath)
        if (dir.exists() && dir.isDirectory()) {
            dir.eachFileRecurse(groovy.io.FileType.FILES) { file ->
                if (file.name.endsWith('.jar')) {
                    classpath += project.files(file)
                }
            }
        } else {
            project.logger.warn("Directory not found or is not a directory: $dirPath")
        }
    }

    private File createClasspathFile(Project project, String testClassName, Set<File> classpath) {
        def classpathFile = project.file("pitest-classpath-${testClassName}.txt")
        classpathFile.withWriter { writer ->
            classpath.each { entry -> writer.writeLine entry.absolutePath }
        }
        return classpathFile
    }

    private String getPitestClasspath(Project project, PitestExtension extension) {
        def pitestJars = getPitestJars(project, extension)
        return pitestJars.collect { it.absolutePath }.join(File.pathSeparator)
    }

    private Set<File> getPitestJars(Project project, PitestExtension extension) {
        if (extension.pitestJarsDirectory) {
            def pitestDir = new File(extension.pitestJarsDirectory)
            if (pitestDir.exists() && pitestDir.isDirectory()) {
                return pitestDir.listFiles().findAll { it.name.startsWith("pitest-") && it.name.endsWith(".jar") }.toSet()
            }
            project.logger.warn("Pitest JARs directory not found or is not a directory: ${extension.pitestJarsDirectory}")
        }
        return project.configurations.testRuntimeClasspath.files.findAll { it.name.startsWith("pitest-") }.toSet()
    }

    private String getSourceDirs(Project project) {
        return project.sourceSets.main.allJava.srcDirs.join(",")
    }

    private String getTargetClass(String testClassName) {
        return testClassName.replaceAll(/^Test|Test$/, "")
    }

    private File getReportDir(Project project, String targetClass) {
        return project.file("build/reports/pitest/$targetClass")
    }

    private List<String> buildPitestCommand(PitestExtension extension, File reportDir, String sourceDirs, String targetClass, String testClassName, File classpathFile, String pitestCp) {
        def command = [
            'java', '-cp', pitestCp, 'org.pitest.mutationtest.commandline.MutationCoverageReport',
            '--reportDir', reportDir.toString(),
            '--sourceDirs', sourceDirs,
            '--targetClasses', targetClass,
            '--targetTests', testClassName,
            '--classPathFile', classpathFile.absolutePath,
            '--outputFormats', extension.outputFormats.join(','),
            '--threads', extension.threads.toString(),
            '--timeoutConst', extension.timeoutConst.toString(),
            '--mutators', extension.mutators.join(','),
            '--timeoutFactor', extension.timeoutFactor.toString(),
            '--verbose', extension.verbose.toString()
        ]

        if (!extension.timestampedReports) {
            command += ['--timestampedReports', 'false']
        }

        if (extension.maxMutationsPerClass > 0) {
            command += ['--maxMutationsPerClass', extension.maxMutationsPerClass.toString()]
        }

        if (!extension.includedTestNGGroups.isEmpty()) {
            command += ['--includedTestNGGroups', extension.includedTestNGGroups.join(',')]
        }

        if (!extension.excludedTestNGGroups.isEmpty()) {
            command += ['--excludedTestNGGroups', extension.excludedTestNGGroups.join(',')]
        }

        if (!extension.avoidCallsTo.isEmpty()) {
            command += ['--avoidCallsTo', extension.avoidCallsTo.join(',')]
        }

        // Add any other Pitest options that correspond to the extension fields

        return command
    }

    private void logCommand(File commandLogFile, List<String> pitestCommand) {
        commandLogFile.text = pitestCommand.join(' ')
    }

    private boolean executePitestCommand(List<String> pitestCommand, Map logFiles) {
        def process = pitestCommand.execute()
        def output = new StringBuilder()
        def error = new StringBuilder()
        process.consumeProcessOutput(output, error)
        process.waitForOrKill(1800000) // Wait for 30 minutes or kill

        logFiles.outputLog.text = output.toString()
        logFiles.errorLog.text = error.toString()

        return process.exitValue() == 0
    }

    private List<String> findMissingClasses(File errorLogFile) {
        def missingClasses = []
        errorLogFile.eachLine { line ->
            if (line.contains("java.lang.ClassNotFoundException")) {
                def matcher = line =~ /java\.lang\.ClassNotFoundException: ([\w.]+)/
                if (matcher.find()) {
                    missingClasses.add(matcher.group(1))
                }
            }
        }
    return missingClasses
}
}