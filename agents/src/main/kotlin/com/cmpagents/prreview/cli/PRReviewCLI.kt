package com.cmpagents.prreview.cli

import com.cmpagents.prreview.PRReviewAgent
import com.cmpagents.prreview.models.RepositoryInfo
import com.cmpagents.prreview.parser.PRParser
import com.cmpagents.prreview.provider.GitProviderFactory
import com.cmpagents.prreview.analyzer.CodeAnalyzer
import com.cmpagents.prreview.reviewer.ReviewGenerator
import com.cmpagents.prreview.rules.Rulebook
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Command-line interface for PR Review Agent
 */
class PRReviewCLI {
    
    fun run(args: Array<String>) {
        if (args.isEmpty() || args[0] == "--help" || args[0] == "-h") {
            printUsage()
            return
        }
        
        val input = args[0]
        val token = System.getenv("GITHUB_TOKEN") ?: System.getenv("GITLAB_TOKEN")
        
        // Try to extract repository info from git config
        val repositoryInfo = extractRepositoryInfo()
        
        // Create agent
        val agent = createAgent(token)
        
        // Run review
        runBlocking {
            try {
                val result = agent.review(input, repositoryInfo)
                
                when (result) {
                    is com.cmpagents.prreview.ReviewResult.Success -> {
                        printReviewResult(result)
                    }
                    is com.cmpagents.prreview.ReviewResult.Failure -> {
                        System.err.println("Error: ${result.error}")
                        result.details?.let { System.err.println(it) }
                        System.exit(1)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Failed to review PR: ${e.message}")
                e.printStackTrace()
                System.exit(1)
            }
        }
    }
    
    private fun createAgent(token: String?): PRReviewAgent {
        // Load rulebook - try multiple locations
        val rulebookPaths = listOf(
            "agents/rules/pr-review.md",
            ".cmp-agents/rules/pr-review.md",
            "rules/pr-review.md"
        )
        val rulebookPath = rulebookPaths.firstOrNull { java.io.File(it).exists() }
        
        val rulebook = Rulebook(rulebookPath ?: "agents/rules/pr-review.md")
        val codeAnalyzer = CodeAnalyzer(rulebook)
        val reviewGenerator = ReviewGenerator(rulebook)
        
        return PRReviewAgent(codeAnalyzer, reviewGenerator, token, rulebookPath)
    }
    
    private fun extractRepositoryInfo(): RepositoryInfo? {
        val gitConfig = File(".git/config")
        if (!gitConfig.exists()) {
            return null
        }
        
        val configContent = gitConfig.readText()
        val remotePattern = Regex("\\[remote \"origin\"\\]\\s+url\\s*=\\s*(.+)", RegexOption.MULTILINE)
        val match = remotePattern.find(configContent)
        
        if (match != null) {
            val remoteUrl = match.groupValues[1].trim()
            return PRParser.extractRepositoryFromGitRemote(remoteUrl)
        }
        
        return null
    }
    
    private fun printReviewResult(result: com.cmpagents.prreview.ReviewResult.Success) {
        println("=".repeat(80))
        println("PR Review Results")
        println("=".repeat(80))
        println()
        println("PR: #${result.prInfo.number} - ${result.prDetails.title}")
        println("Author: ${result.prDetails.author}")
        println("URL: ${result.prInfo.url}")
        println()
        
        // Print summary
        println("Summary:")
        println("  Status: ${result.summary.status}")
        println("  Files Reviewed: ${result.summary.filesReviewed}")
        println("  Lines Added: ${result.summary.linesAdded}")
        println("  Lines Removed: ${result.summary.linesRemoved}")
        println("  Total Comments: ${result.summary.totalComments}")
        println("  Critical Issues: ${result.summary.criticalIssues}")
        println("  Warnings: ${result.summary.warnings}")
        println("  Suggestions: ${result.summary.suggestions}")
        println("  KMP Issues: ${result.summary.kmpIssuesFound}")
        println()
        
        // Print affected platforms
        if (result.analysis.affectedPlatforms.isNotEmpty()) {
            println("Affected Platforms: ${result.analysis.affectedPlatforms.joinToString()}")
            println()
        }
        
        // Print review comments grouped by severity
        val criticalComments = result.reviewComments.filter { it.severity == com.cmpagents.prreview.models.ReviewSeverity.CRITICAL }
        val warningComments = result.reviewComments.filter { it.severity == com.cmpagents.prreview.models.ReviewSeverity.WARNING }
        val suggestionComments = result.reviewComments.filter { it.severity == com.cmpagents.prreview.models.ReviewSeverity.SUGGESTION }
        
        if (criticalComments.isNotEmpty()) {
            println("🚨 CRITICAL ISSUES:")
            println("-".repeat(80))
            criticalComments.forEach { comment ->
                printComment(comment)
            }
            println()
        }
        
        if (warningComments.isNotEmpty()) {
            println("⚠️  WARNINGS:")
            println("-".repeat(80))
            warningComments.take(10).forEach { comment ->
                printComment(comment)
            }
            if (warningComments.size > 10) {
                println("... and ${warningComments.size - 10} more warnings")
            }
            println()
        }
        
        if (suggestionComments.isNotEmpty()) {
            println("💡 SUGGESTIONS:")
            println("-".repeat(80))
            suggestionComments.take(5).forEach { comment ->
                printComment(comment)
            }
            if (suggestionComments.size > 5) {
                println("... and ${suggestionComments.size - 5} more suggestions")
            }
            println()
        }
        
        // Save detailed report
        saveDetailedReport(result)
    }
    
    private fun printComment(comment: com.cmpagents.prreview.models.ReviewComment) {
        println("File: ${comment.file}")
        comment.line?.let { println("Line: $it") }
        println("Category: ${comment.category}")
        println("Message: ${comment.message}")
        comment.suggestion?.let { println("Suggestion: $it") }
        comment.codeSnippet?.let { println("Code:\n$it") }
        println()
    }
    
    private fun saveDetailedReport(result: com.cmpagents.prreview.ReviewResult.Success) {
        val reportDir = File(".cmp-agents")
        reportDir.mkdirs()
        
        val reportFile = File(reportDir, "pr-review-${result.prInfo.number}.md")
        val report = buildDetailedReport(result)
        reportFile.writeText(report)
        
        println("📄 Detailed report saved to: ${reportFile.absolutePath}")
    }
    
    private fun buildDetailedReport(result: com.cmpagents.prreview.ReviewResult.Success): String {
        val sb = StringBuilder()
        sb.appendLine("# PR Review Report")
        sb.appendLine()
        sb.appendLine("**PR:** #${result.prInfo.number} - ${result.prDetails.title}")
        sb.appendLine("**Author:** ${result.prDetails.author}")
        sb.appendLine("**URL:** ${result.prInfo.url}")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("- Status: ${result.summary.status}")
        sb.appendLine("- Files Reviewed: ${result.summary.filesReviewed}")
        sb.appendLine("- Lines Added: ${result.summary.linesAdded}")
        sb.appendLine("- Lines Removed: ${result.summary.linesRemoved}")
        sb.appendLine("- Total Comments: ${result.summary.totalComments}")
        sb.appendLine()
        sb.appendLine("## Review Comments")
        sb.appendLine()
        
        result.reviewComments.forEach { comment ->
            sb.appendLine("### ${comment.file}")
            comment.line?.let { sb.appendLine("**Line:** $it") }
            sb.appendLine("**Severity:** ${comment.severity}")
            sb.appendLine("**Category:** ${comment.category}")
            sb.appendLine()
            sb.appendLine(comment.message)
            comment.suggestion?.let {
                sb.appendLine()
                sb.appendLine("**Suggestion:** $it")
            }
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    private fun printUsage() {
        println("""
            PR Review Agent - AI-powered code review for PRs/MRs
            Follows rules defined in agents/rules/pr-review.md
            
            Usage:
              pr-review <PR_URL_OR_NUMBER> [OPTIONS]
            
            Arguments:
              PR_URL_OR_NUMBER    PR/MR URL (e.g., https://github.com/owner/repo/pull/123)
                                  or PR number (e.g., 123) if repository info is available
            
            Environment Variables:
              GITHUB_TOKEN        GitHub personal access token (optional, for private repos)
              GITLAB_TOKEN        GitLab access token (optional, for private repos)
            
            Rulebook:
              The agent follows rules defined in agents/rules/pr-review.md
              Customize review criteria by editing this file
            
            Examples:
              pr-review https://github.com/owner/repo/pull/123
              pr-review 123
              GITHUB_TOKEN=xxx pr-review 456
            
            Supported Providers:
              - GitHub (github.com)
              - GitLab (gitlab.com) - coming soon
              - Bitbucket (bitbucket.org) - coming soon
        """.trimIndent())
    }
}

fun main(args: Array<String>) {
    PRReviewCLI().run(args)
}

