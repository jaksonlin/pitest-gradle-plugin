package com.github.jaksonlin.pitest.report

class MutationScoreReporter {
    static class FunctionScore {
        String className
        String methodName
        String methodDescription
        int totalMutants
        int killedMutants
        int survivedMutants
        double score

        String toString() {
            """Function: ${className}#${methodName}${methodDescription}
  Mutation Score: ${String.format("%.2f", score * 100)}%
  Total Mutants: ${totalMutants}
  Killed: ${killedMutants}
  Survived: ${survivedMutants}
"""
        }
    }

    String generateReport(Mutations mutations) {
        def functionScores = mutations.mutation
            .groupBy { "${it.mutatedClass}#${it.mutatedMethod}${it.methodDescription}" }
            .collect { key, mutants ->
                def totalMutants = mutants.size()
                def killedMutants = mutants.count { it.status == "KILLED" }
                def survivedMutants = mutants.count { it.status == "SURVIVED" }
                
                new FunctionScore(
                    className: mutants.first().mutatedClass,
                    methodName: mutants.first().mutatedMethod,
                    methodDescription: mutants.first().methodDescription,
                    totalMutants: totalMutants,
                    killedMutants: killedMutants,
                    survivedMutants: survivedMutants,
                    score: killedMutants / totalMutants
                )
            }
            .sort { -it.score }

        def totalMutants = functionScores.sum { it.totalMutants }
        def totalKilled = functionScores.sum { it.killedMutants }
        def overallScore = totalKilled / totalMutants

        """=== Mutation Testing Report ===

Overall Project Score: ${String.format("%.2f", overallScore * 100)}%
Total Mutants: ${totalMutants}
Total Killed: ${totalKilled}

=== Function-Level Scores ===
${functionScores.join('\n')}"""
    }
}