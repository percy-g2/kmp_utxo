package com.cmpagents.prreview

import com.cmpagents.prreview.analyzer.CodeAnalyzer
import com.cmpagents.prreview.parser.PRParser
import com.cmpagents.prreview.provider.GitProvider
import com.cmpagents.prreview.reviewer.ReviewGenerator
import com.cmpagents.prreview.rules.Rulebook

/**
 * AI-powered PR/MR Review Agent
 * Analyzes pull requests and merge requests to provide intelligent code review feedback
 */
class PRReviewAgent(
    private val codeAnalyzer: CodeAnalyzer,
    private val reviewGenerator: ReviewGenerator,
    private val token: String? = null,
    private val rulebookPath: String? = null
) {
    
    private val rulebook: Rulebook by lazy {
        Rulebook(rulebookPath ?: "agents/rules/pr-review.md")
    }
    
    /**
     * Review a PR/MR by URL or number
     * @param input PR/MR URL (e.g., https://github.com/owner/repo/pull/123) or number (e.g., "123")
     * @param repositoryInfo Optional repository info if using PR number
     */
    suspend fun review(
        input: String,
        repositoryInfo: RepositoryInfo? = null
    ): ReviewResult {
        return try {
            // Parse the input to extract PR/MR information
            val prInfo = PRParser.parse(input, repositoryInfo)
            
            // Get appropriate git provider for this PR
            val gitProvider = GitProviderFactory.create(prInfo.provider, token)
            
            // Fetch PR/MR details from the git provider
            val prDetails = gitProvider.fetchPRDetails(prInfo)
            
            // Analyze code changes
            val analysis = codeAnalyzer.analyze(prDetails)
            
            // Generate review comments
            val reviewComments = reviewGenerator.generateReview(prDetails, analysis)
            
            ReviewResult.Success(
                prInfo = prInfo,
                prDetails = prDetails,
                analysis = analysis,
                reviewComments = reviewComments,
                summary = generateSummary(prDetails, analysis, reviewComments)
            )
        } catch (e: Exception) {
            ReviewResult.Failure(
                error = e.message ?: "Unknown error occurred",
                details = e.stackTraceToString()
            )
        }
    }
    
    private fun generateSummary(
        prDetails: PRDetails,
        analysis: CodeAnalysis,
        reviewComments: List<ReviewComment>
    ): ReviewSummary {
        val criticalIssues = reviewComments.count { it.severity == ReviewSeverity.CRITICAL }
        val warnings = reviewComments.count { it.severity == ReviewSeverity.WARNING }
        val suggestions = reviewComments.count { it.severity == ReviewSeverity.SUGGESTION }
        
        val overallStatus = when {
            criticalIssues > 0 -> ReviewStatus.NEEDS_WORK
            warnings > 5 -> ReviewStatus.NEEDS_IMPROVEMENT
            else -> ReviewStatus.APPROVED_WITH_SUGGESTIONS
        }
        
        return ReviewSummary(
            status = overallStatus,
            totalComments = reviewComments.size,
            criticalIssues = criticalIssues,
            warnings = warnings,
            suggestions = suggestions,
            filesReviewed = prDetails.files.size,
            linesAdded = prDetails.files.sumOf { it.additions },
            linesRemoved = prDetails.files.sumOf { it.deletions },
            kmpIssuesFound = analysis.kmpIssues.size,
            testCoverage = analysis.testCoverage
        )
    }
}

/**
 * Result of PR review
 */
sealed class ReviewResult {
    data class Success(
        val prInfo: PRInfo,
        val prDetails: PRDetails,
        val analysis: CodeAnalysis,
        val reviewComments: List<ReviewComment>,
        val summary: ReviewSummary
    ) : ReviewResult()
    
    data class Failure(
        val error: String,
        val details: String? = null
    ) : ReviewResult()
}

/**
 * Review summary
 */
data class ReviewSummary(
    val status: ReviewStatus,
    val totalComments: Int,
    val criticalIssues: Int,
    val warnings: Int,
    val suggestions: Int,
    val filesReviewed: Int,
    val linesAdded: Int,
    val linesRemoved: Int,
    val kmpIssuesFound: Int,
    val testCoverage: Double? = null
)

enum class ReviewStatus {
    APPROVED,
    APPROVED_WITH_SUGGESTIONS,
    NEEDS_IMPROVEMENT,
    NEEDS_WORK
}

