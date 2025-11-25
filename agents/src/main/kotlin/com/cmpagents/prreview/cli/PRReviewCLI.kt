package com.cmpagents.prreview.cli

import com.cmpagents.prreview.PRReviewAgent
import com.cmpagents.prreview.models.*
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
        
        val command = args[0]
        
        when {
            command == "review" && args.size > 1 -> {
                handleReview(args[1])
            }
            command == "create-pr" -> {
                handleCreatePR(args.drop(1).toTypedArray())
            }
            command == "push" -> {
                handlePush(args.drop(1).toTypedArray())
            }
            command == "auth" -> {
                handleAuth()
            }
            // Backward compatibility: if first arg looks like URL or number, treat as review
            command.startsWith("http") || command.matches(Regex("\\d+")) -> {
                handleReview(command)
            }
            else -> {
                System.err.println("Unknown command: $command")
                printUsage()
                System.exit(1)
            }
        }
    }
    
    private fun handleReview(input: String) {
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
    
    private fun handleCreatePR(args: Array<String>) {
        runBlocking {
            try {
                val gitHubCLI = com.cmpagents.prreview.github.GitHubCLI()
                
                // Check if GitHub CLI is installed and authenticated
                if (!gitHubCLI.isInstalled()) {
                    System.err.println("Error: GitHub CLI (gh) is not installed.")
                    System.err.println("Install it from: https://cli.github.com/")
                    System.exit(1)
                    return@runBlocking
                }
                
                if (!gitHubCLI.isAuthenticated()) {
                    System.err.println("Error: Not authenticated with GitHub CLI.")
                    System.err.println("Run: gh auth login")
                    System.exit(1)
                    return@runBlocking
                }
                
                // Parse arguments
                var title = ""
                var body = ""
                var baseBranch = "main"
                var headBranch: String? = null
                var draft = false
                
                var i = 0
                while (i < args.size) {
                    when (args[i]) {
                        "--title", "-t" -> {
                            title = args.getOrNull(++i) ?: ""
                        }
                        "--body", "-b" -> {
                            body = args.getOrNull(++i) ?: ""
                        }
                        "--base" -> {
                            baseBranch = args.getOrNull(++i) ?: "main"
                        }
                        "--head", "--branch", "-b" -> {
                            headBranch = args.getOrNull(++i)
                        }
                        "--draft" -> {
                            draft = true
                        }
                    }
                    i++
                }
                
                // Get current branch if head not specified
                val changePusher = com.cmpagents.prreview.github.ChangePusher()
                val currentBranch = headBranch ?: changePusher.getCurrentBranch()
                
                if (currentBranch == null) {
                    System.err.println("Error: Could not determine branch. Specify with --head")
                    System.exit(1)
                    return@runBlocking
                }
                
                // Generate title if not provided
                if (title.isEmpty()) {
                    title = "Update: ${currentBranch}"
                }
                
                val prCreator = com.cmpagents.prreview.github.PRCreator(gitHubCLI)
                val result = prCreator.createPRFromChanges(
                    title = title,
                    baseBranch = baseBranch,
                    headBranch = currentBranch,
                    draft = draft,
                    autoGenerateBody = body.isEmpty()
                )
                
                when (result) {
                    is com.cmpagents.prreview.github.PRResult.Success -> {
                        println("✅ Pull Request created successfully!")
                        println("🔗 ${result.prUrl}")
                    }
                    is com.cmpagents.prreview.github.PRResult.Failure -> {
                        System.err.println("❌ Failed to create PR: ${result.error}")
                        System.exit(1)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error creating PR: ${e.message}")
                e.printStackTrace()
                System.exit(1)
            }
        }
    }
    
    private fun handlePush(args: Array<String>) {
        runBlocking {
            try {
                val changePusher = com.cmpagents.prreview.github.ChangePusher()
                
                // Check for uncommitted changes
                if (!changePusher.hasUncommittedChanges()) {
                    System.err.println("No uncommitted changes to push.")
                    System.exit(0)
                    return@runBlocking
                }
                
                // Parse arguments
                var message = ""
                var branch: String? = null
                var force = false
                var files: List<String>? = null
                
                var i = 0
                while (i < args.size) {
                    when (args[i]) {
                        "--message", "-m" -> {
                            message = args.getOrNull(++i) ?: ""
                        }
                        "--branch", "-b" -> {
                            branch = args.getOrNull(++i)
                        }
                        "--force" -> {
                            force = true
                        }
                        "--files" -> {
                            val filesList = args.getOrNull(++i)?.split(",") ?: emptyList()
                            files = filesList
                        }
                    }
                    i++
                }
                
                // Get current branch if not specified
                val currentBranch = branch ?: changePusher.getCurrentBranch()
                
                if (message.isEmpty()) {
                    message = "Update: ${currentBranch ?: "changes"}"
                }
                
                val result = changePusher.stageCommitAndPush(message, currentBranch, files)
                
                when (result) {
                    is com.cmpagents.prreview.github.PushResult.Success -> {
                        println("✅ Changes pushed successfully!")
                        println(result.output)
                    }
                    is com.cmpagents.prreview.github.PushResult.Failure -> {
                        System.err.println("❌ Failed to push: ${result.error}")
                        System.exit(1)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error pushing changes: ${e.message}")
                e.printStackTrace()
                System.exit(1)
            }
        }
    }
    
    private fun handleAuth() {
        runBlocking {
            try {
                val gitHubCLI = com.cmpagents.prreview.github.GitHubCLI()
                
                if (!gitHubCLI.isInstalled()) {
                    System.err.println("Error: GitHub CLI (gh) is not installed.")
                    System.err.println("Install it from: https://cli.github.com/")
                    System.exit(1)
                    return@runBlocking
                }
                
                if (gitHubCLI.isAuthenticated()) {
                    val user = gitHubCLI.getAuthenticatedUser()
                    println("✅ Already authenticated as: ${user?.login ?: "unknown"}")
                    return@runBlocking
                }
                
                println("🔐 Authenticating with GitHub CLI...")
                println("This will open your browser for authentication.")
                
                val authenticated = gitHubCLI.authenticate()
                
                if (authenticated) {
                    val user = gitHubCLI.getAuthenticatedUser()
                    println("✅ Successfully authenticated as: ${user?.login ?: "unknown"}")
                } else {
                    System.err.println("❌ Authentication failed")
                    System.exit(1)
                }
            } catch (e: Exception) {
                System.err.println("Error during authentication: ${e.message}")
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
            PR Review Agent - AI-powered code review and PR management
            Follows rules defined in agents/rules/pr-review.md
            
            Commands:
              review <PR_URL_OR_NUMBER>    Review a PR/MR
              create-pr [OPTIONS]          Create a new Pull Request
              push [OPTIONS]               Push changes to GitHub
              auth                         Authenticate with GitHub CLI
            
            Review Command:
              pr-review review <PR_URL_OR_NUMBER>
              
              Examples:
                pr-review review https://github.com/owner/repo/pull/123
                pr-review review 123
                pr-review https://github.com/owner/repo/pull/123  (backward compatible)
            
            Create PR Command:
              pr-review create-pr [OPTIONS]
              
              Options:
                --title, -t <title>        PR title (default: auto-generated)
                --body, -b <body>          PR body/description (default: auto-generated)
                --base <branch>            Base branch (default: main)
                --head, --branch <branch>  Head branch (default: current branch)
                --draft                    Create as draft PR
            
              Examples:
                pr-review create-pr --title "Fix bug" --body "Description"
                pr-review create-pr --draft
                pr-review create-pr --title "Update deps" --base main --head feature-branch
            
            Push Command:
              pr-review push [OPTIONS]
              
              Options:
                --message, -m <message>    Commit message (required)
                --branch, -b <branch>      Branch to push to (default: current branch)
                --force                    Force push (use with caution)
                --files <file1,file2>      Specific files to commit
            
              Examples:
                pr-review push --message "Fix bug"
                pr-review push -m "Update" --branch feature-branch
                pr-review push -m "Update" --files "file1.kt,file2.kt"
            
            Auth Command:
              pr-review auth
              
              Authenticates with GitHub CLI (opens browser)
            
            Environment Variables:
              GITHUB_TOKEN        GitHub personal access token (optional, for private repos)
              GITLAB_TOKEN        GitLab access token (optional, for private repos)
            
            GitHub CLI:
              This tool uses GitHub CLI (gh) for authentication and PR creation.
              Install from: https://cli.github.com/
              Authenticate: pr-review auth
            
            Rulebook:
              The agent follows rules defined in agents/rules/pr-review.md
              Customize review criteria by editing this file
            
            Supported Providers:
              - GitHub (github.com) - Full support with GitHub CLI
              - GitLab (gitlab.com) - coming soon
              - Bitbucket (bitbucket.org) - coming soon
        """.trimIndent())
    }
}

fun main(args: Array<String>) {
    PRReviewCLI().run(args)
}

