package com.cmpagents.prreview.reviewer

import com.cmpagents.prreview.models.*
import com.cmpagents.prreview.rules.Rulebook

/**
 * Generates intelligent review comments based on code analysis
 * Follows rules defined in the PR review rulebook
 */
class ReviewGenerator(
    private val rulebook: Rulebook = Rulebook()
) {
    
    fun generateReview(
        prDetails: PRDetails,
        analysis: CodeAnalysis
    ): List<ReviewComment> {
        val comments = mutableListOf<ReviewComment>()
        
        // Convert KMP issues to review comments (following rulebook severity levels)
        analysis.kmpIssues.forEach { issue ->
            comments.add(
                ReviewComment(
                    file = issue.file,
                    line = issue.line,
                    severity = rulebook.getSeverityForIssue(issue.type),
                    message = issue.message,
                    suggestion = issue.suggestion,
                    category = ReviewCategory.KMP_ARCHITECTURE
                )
            )
        }
        
        // Convert code quality issues to review comments
        analysis.codeQualityIssues.forEach { issue ->
            comments.add(
                ReviewComment(
                    file = issue.file,
                    line = issue.line,
                    severity = ReviewSeverity.SUGGESTION,
                    message = issue.message,
                    suggestion = issue.suggestion,
                    category = ReviewCategory.CODE_QUALITY
                )
            )
        }
        
        // Convert security issues to review comments (following rulebook severity levels)
        analysis.securityIssues.forEach { issue ->
            comments.add(
                ReviewComment(
                    file = issue.file,
                    line = issue.line,
                    severity = rulebook.getSeverityForSecurity(issue.type),
                    message = issue.message,
                    suggestion = issue.suggestion,
                    category = ReviewCategory.SECURITY
                )
            )
        }
        
        // Add general review comments
        comments.addAll(generateGeneralComments(prDetails, analysis))
        
        return comments
    }
    
    private fun generateGeneralComments(
        prDetails: PRDetails,
        analysis: CodeAnalysis
    ): List<ReviewComment> {
        val comments = mutableListOf<ReviewComment>()
        
        // Check if PR affects multiple platforms
        if (analysis.affectedPlatforms.size > 1) {
            comments.add(
                ReviewComment(
                    file = "",
                    line = null,
                    severity = ReviewSeverity.INFO,
                    message = "This PR affects multiple platforms: ${analysis.affectedPlatforms.joinToString()}. " +
                            "Please ensure testing is performed on all affected platforms.",
                    suggestion = null,
                    category = ReviewCategory.TESTING
                )
            )
        }
        
        // Check for test files (following rulebook Rule 11)
        val hasTestFiles = prDetails.files.any { it.path.contains("test") || it.path.contains("Test") }
        if (!hasTestFiles && rulebook.requiresTests(prDetails.files.size)) {
            comments.add(
                ReviewComment(
                    file = "",
                    line = null,
                    severity = ReviewSeverity.SUGGESTION,
                    message = "No test files detected in this PR (${prDetails.files.size} files changed). " +
                            "Consider adding tests for the changes (Rule 11: Test Coverage).",
                    suggestion = "Add unit tests in commonTest or platform-specific test directories as per PR review rulebook",
                    category = ReviewCategory.TESTING
                )
            )
        }
        
        // Check for documentation
        val hasDocumentation = prDetails.files.any { 
            it.path.endsWith(".md") || it.path.contains("README")
        }
        if (!hasDocumentation && prDetails.files.any { it.path.contains("api") || it.path.contains("public") }) {
            comments.add(
                ReviewComment(
                    file = "",
                    line = null,
                    severity = ReviewSeverity.SUGGESTION,
                    message = "Consider adding documentation for public APIs or significant changes.",
                    suggestion = null,
                    category = ReviewCategory.DOCUMENTATION
                )
            )
        }
        
        return comments
    }
}

