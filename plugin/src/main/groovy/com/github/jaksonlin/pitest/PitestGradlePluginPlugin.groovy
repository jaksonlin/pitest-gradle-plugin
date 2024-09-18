package com.github.jaksonlin.pitest

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult


class PitestPlugin implements Plugin<Project> {
    private enum PitestResult {
        SUCCESS, NO_MUTATIONS, FAILURE
    }

    void apply(Project project) {
        // Create the extension
        def extension = project.extensions.create('pitestConfig', PitestExtension)

        // Apply the Java plugin if not already applied
        project.plugins.apply('java')

        // Configure ByteBuddy agent
        configureByteBuddyAgent(project)

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
            def logFiles = createLogFiles(project, testClassName)
            def classpath = buildClasspath(project, extension)
            def classpathFile = createClasspathFile(project, testClassName, classpath)
                def pitestCp = getPitestClasspath(project, extension)
                def sourceDirs = getSourceDirs(project)
                def targetClass = getTargetClass(testClassName)
                def reportDir = getReportDir(project, targetClass)

                def pitestCommand = buildPitestCommand(extension, project, reportDir, sourceDirs, targetClass, testClassName, classpathFile, pitestCp)
                project.logger.info("Pitest command for $testClassName: ${pitestCommand.join(' ')}")
            
                if (pitestCommand.isEmpty()) {
                    project.logger.error("Pitest command is empty for $testClassName")
                    return false
                }

                logCommand(logFiles.commandLog, pitestCommand)
                if (extension.useByteBuddyAgent) {
                    project.logger.info("Running Pitest with ByteBuddy agent for $testClassName")
                } else {
                    project.logger.info("Running Pitest without ByteBuddy agent for $testClassName")
                }

                def result = executePitestCommand(pitestCommand, logFiles)
                
                switch (result) {
                    case PitestResult.SUCCESS:
                        project.logger.info("Pitest succeeded for $testClassName")
                        return true
                    case PitestResult.NO_MUTATIONS:
                        project.logger.info("No mutations found for $testClassName. This is likely a POJO or empty class.")
                        return true
                    case PitestResult.FAILURE:
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
                    default:
                        project.logger.error("Unknown Pitest result: $result")
                        return false
                }

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
        
        // Ensure necessary JARs are in the classpath
        extension.additionalRequiredJars.each { jarName ->
            def jar = findJar(project, extension, jarName)
            if (jar) {
                classpath.add(jar)
            }
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
        def additionalJars = extension.additionalRequiredJars.collect { jarName ->
            findJar(project, extension, jarName)
        }.findAll { it != null }
        
        pitestJars.addAll(additionalJars)
        
        additionalJars.each { jar ->
            if (jar) {
                project.logger.info("Added ${jar.name} to Pitest classpath")
            }
        }
        
        if (additionalJars.size() < extension.additionalRequiredJars.size()) {
            project.logger.warn("Some dependencies might be missing. Pitest may fail or have limited functionality.")
        }

        def classpath = pitestJars.collect { it.absolutePath }.join(File.pathSeparator)
    
        if (classpath.isEmpty()) {
            project.logger.error("Pitest classpath is empty")
            throw new IllegalStateException("Pitest classpath is empty")
        }
        
        return classpath
    }

    private Set<File> getPitestJars(Project project, PitestExtension extension) {
        def pitestJars = new HashSet<File>()
        if (extension.pitestJarsDirectory) {
            def pitestDir = new File(extension.pitestJarsDirectory)
            if (pitestDir.exists() && pitestDir.isDirectory()) {
                pitestJars.addAll(pitestDir.listFiles().findAll { it.name.startsWith("pitest-") && it.name.endsWith(".jar") })
            } else {
                project.logger.warn("Pitest JARs directory not found or is not a directory: ${extension.pitestJarsDirectory}")
            }
        }
        if (pitestJars.isEmpty()) {
            pitestJars.addAll(project.configurations.testRuntimeClasspath.files.findAll { it.name.startsWith("pitest-") })
        }
        return pitestJars
    }

    private File findJar(Project project, PitestExtension extension, String jarNamePart) {
        // Look in the project's runtime classpath
        def jar = project.configurations.runtimeClasspath.find { it.name.contains(jarNamePart) }
        
        // If not found, search in additional JAR directories
        if (!jar) {
            extension.additionalJarDirectories.each { dirPath ->
                def dir = new File(dirPath)
                if (dir.exists() && dir.isDirectory()) {
                    def foundJar = dir.listFiles().find { it.name.contains(jarNamePart) && it.name.endsWith('.jar') }
                    if (foundJar) {
                        jar = foundJar
                        return true // break the each loop
                    }
                }
            }
        }
        
        return jar
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

    private List<String> buildPitestCommand(PitestExtension extension, Project project, File reportDir, String sourceDirs, String targetClass, String testClassName, File classpathFile, String pitestCp) {
        assert extension != null : "Extension is null"
        assert project != null : "Project is null"
        assert reportDir != null : "Report directory is null"
        assert sourceDirs != null : "Source directories are null"
        assert targetClass != null : "Target class is null"
        assert testClassName != null : "Test class name is null"
        assert classpathFile != null : "Classpath file is null"
        assert pitestCp != null : "Pitest classpath is null"

        // replicate the agent option in starting the java, so that in the separated jvm forked by pitest, it will have byte buddy agent to fix the issue of inline mockito
        def byteBuddyAgent = project.configurations.testRuntimeClasspath.find { it.name.contains('byte-buddy-agent') }
    
        def command = [
            'java'
        ]
        
        if (extension.useByteBuddyAgent && byteBuddyAgent) {
            command += ["-javaagent:${byteBuddyAgent.absolutePath}"]
        }
        
        command += [
            '-cp', pitestCp,
            'org.pitest.mutationtest.commandline.MutationCoverageReport',
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

    private PitestResult executePitestCommand(List<String> pitestCommand, Map logFiles) {
        def process = pitestCommand.execute()
        def output = new StringBuilder()
        def error = new StringBuilder()
        process.consumeProcessOutput(output, error)
        process.waitForOrKill(1800000) // Wait for 30 minutes or kill

        logFiles.outputLog.text = output.toString()
        logFiles.errorLog.text = error.toString()

        if (process.exitValue() == 0) {
            return PitestResult.SUCCESS
        } else if (output.toString().contains("No mutations found") || error.toString().contains("No mutations found")) {
            return PitestResult.NO_MUTATIONS
        } else {
            return PitestResult.FAILURE
        }
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

    private boolean isByteBuddyAgentConfigured(Project project) {
        return project.tasks.withType(Test).any { testTask ->
            testTask.jvmArgs.any { arg -> arg.contains('byte-buddy-agent') }
        }
    }

    // address issue https://github.com/mockito/mockito/issues/1879, when user use inline mockito, it will have byte-buddy-agent in classpath, add it into javaagent as per discussion in the issue
    private void configureByteBuddyAgent(Project project) {
        if (!isByteBuddyAgentConfigured(project)) {
            project.afterEvaluate {
                project.tasks.withType(Test).each { testTask ->
                    testTask.doFirst {
                        def byteBuddyAgent = project.configurations.testRuntimeClasspath.find { it.name.contains('byte-buddy-agent') }
                        if (byteBuddyAgent) {
                            testTask.jvmArgs "-javaagent:${byteBuddyAgent.absolutePath}"
                        }
                    }
                }
            }
        }
    }

    private void checkRequiredDependencies(Project project, PitestExtension extension) {
        def missingJars = extension.additionalRequiredJars.findAll { jarName ->
            findJar(project, extension, jarName) == null
        }
        
        if (!missingJars.isEmpty()) {
            project.logger.warn("The following dependencies are missing and may cause Pitest to fail or have limited functionality: ${missingJars.join(', ')}")
            project.logger.warn("Please add these dependencies to your project or specify their location using additionalJarDirectories in the pitestConfig.")
        }
    }
}