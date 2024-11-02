package com.github.jaksonlin.pitest.report

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper

class MutationReportParser {
    private static final XmlMapper xmlMapper = new XmlMapper().tap {
        enable(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE)
        enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    static Mutations parseMutationsFromXml(String filePath) {
        new File(filePath).withInputStream { inputStream ->
            return xmlMapper.readValue(inputStream, Mutations.class)
        }
    }
}

class Mutations {
    boolean partial
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Mutation> mutation
}

class Mutation {
    boolean detected
    String status
    int numberOfTestsRun
    String sourceFile
    String mutatedClass
    String mutatedMethod
    String methodDescription
    int lineNumber
    String mutator
    Indexes indexes
    Blocks blocks
    String killingTest
    String description
}

class Indexes {
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Integer> index
}

class Blocks {
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Integer> block
}