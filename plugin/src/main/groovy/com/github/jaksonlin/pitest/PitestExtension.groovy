package com.github.jaksonlin.pitest

class PitestExtension {
    String pitestVersion = '1.16.0'
    List<String> mutators = ['STRONGER']
    int threads = 4
    int timeoutConst = 5000
    List<String> outputFormats = ['XML']
    String jacocoReportPath = '$project.buildDir/../../target/jacoco/jacocoTest.exec'
    boolean timestampedReports = true
    double timeoutFactor = 1.5
    boolean verbose = false
    boolean tuningMode = false

    // New fields we've added
    List<String> additionalClasspathElements = []
    List<String> additionalJarDirectories = []
    String pitestJarsDirectory = null

    // Fields for the fast mutation testing profile
    int maxMutationsPerClass = 0 // 0 means no limit
    List<String> includedTestNGGroups = []
    List<String> excludedTestNGGroups = []
    List<String> avoidCallsTo = []
    boolean useByteBuddyAgent = true

    // Additional required jars
    List<String> additionalRequiredJars = ['commons-text', 'commons-lang3', 'byte-buddy-agent']
}