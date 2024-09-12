package com.github.jaksonlin.pitest

class PitestExtension {
    String pitestVersion = '1.16.0'
    List<String> mutators = ['STRONGER']
    int threads = 4
    int timeoutConst = 5000
    List<String> outputFormats = ['XML']
    String jacocoReportPath =  '$project.buildDir/../../target/jacoco/jacocoTest.exec'
}