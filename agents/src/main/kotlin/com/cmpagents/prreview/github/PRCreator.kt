package com.cmpagents.prreview.github

import com.cmpagents.prreview.models.RepositoryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Creates Pull Requests using GitHub CLI
 */
class PRCreator(private val gitHubCLI: GitHubCLI) {
    
    /**
     * Create a new Pull Request
     */
    suspend fun createPR(
        title: String,
        body: String,
        baseBranch: String = "main",
        headBranch: String,
        draft: Boolean = false,
        repositoryInfo: RepositoryInfo? = null
    ): PRResult = withContext(Dispatchers.IO) {
        try {
            // Ensure we're on the head branch and it's pushed
            ensureBranchPushed(headBranch)
            
            // Build gh pr create command
            val command = mutableListOf("gh", "pr", "create")
            command.add("--title")
            command.add(title)
            command.add("--body")
            command.add(body)
            command.add("--base")
            command.add(baseBranch)
            command.add("--head")
            command.add(headBranch)
            
            if (draft) {
                command.add("--draft")
            }
            
            // Add repo if specified
            repositoryInfo?.let {
                command.add("--repo")
                command.add("${it.owner}/${it.repository}")
            }
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                // Extract PR URL from output
                val prUrl = extractPRUrl(output)
                PRResult.Success(prUrl ?: output.trim())
            } else {
                PRResult.Failure("Failed to create PR: $output")
            }
        } catch (e: Exception) {
            PRResult.Failure("Error creating PR: ${e.message}")
        }
    }
    
    /**
     * Create PR from file changes with auto-generated description
     */
    suspend fun createPRFromChanges(
        title: String,
        baseBranch: String = "main",
        headBranch: String,
        draft: Boolean = false,
        autoGenerateBody: Boolean = true
    ): PRResult = withContext(Dispatchers.IO) {
        try {
            val body = if (autoGenerateBody) {
                generatePRBody(headBranch, baseBranch)
            } else {
                ""
            }
            
            createPR(title, body, baseBranch, headBranch, draft)
        } catch (e: Exception) {
            PRResult.Failure("Error creating PR from changes: ${e.message}")
        }
    }
    
    /**
     * Ensure branch exists and is pushed to remote
     */
    private suspend fun ensureBranchPushed(branchName: String) = withContext(Dispatchers.IO) {
        try {
            // Check if branch exists locally
            val branchCheck = ProcessBuilder("git", "rev-parse", "--verify", branchName)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val branchExists = branchCheck.waitFor() == 0
            
            if (!branchExists) {
                throw IllegalStateException("Branch $branchName does not exist locally")
            }
            
            // Push branch to remote
            val pushProcess = ProcessBuilder("git", "push", "-u", "origin", branchName)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val pushOutput = pushProcess.inputStream.bufferedReader().readText()
            val pushExitCode = pushProcess.waitFor()
            
            if (pushExitCode != 0 && !pushOutput.contains("already exists")) {
                throw IllegalStateException("Failed to push branch: $pushOutput")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error ensuring branch is pushed: ${e.message}")
        }
    }
    
    /**
     * Generate PR body from git changes
     */
    private suspend fun generatePRBody(headBranch: String, baseBranch: String): String = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            sb.appendLine("## Changes")
            sb.appendLine()
            
            // Get list of changed files
            val diffProcess = ProcessBuilder("git", "diff", "--name-status", "$baseBranch..$headBranch")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val diffOutput = diffProcess.inputStream.bufferedReader().readText()
            val diffExitCode = diffProcess.waitFor()
            
            if (diffExitCode == 0 && diffOutput.isNotEmpty()) {
                val files = diffOutput.lines().filter { it.isNotEmpty() }
                sb.appendLine("### Files Changed")
                files.forEach { file ->
                    val status = file.substring(0, 1)
                    val fileName = file.substring(2)
                    val statusEmoji = when (status) {
                        "A" -> "➕"
                        "M" -> "✏️"
                        "D" -> "❌"
                        "R" -> "🔄"
                        else -> "📝"
                    }
                    sb.appendLine("- $statusEmoji $fileName")
                }
                sb.appendLine()
            }
            
            // Get commit messages
            val logProcess = ProcessBuilder("git", "log", "--oneline", "$baseBranch..$headBranch")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val logOutput = logProcess.inputStream.bufferedReader().readText()
            val logExitCode = logProcess.waitFor()
            
            if (logExitCode == 0 && logOutput.isNotEmpty()) {
                sb.appendLine("### Commits")
                logOutput.lines().take(10).forEach { commit ->
                    sb.appendLine("- $commit")
                }
                sb.appendLine()
            }
            
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("*This PR was created automatically by PR Review Agent*")
            
            sb.toString()
        } catch (e: Exception) {
            "## Changes\n\nAuto-generated PR description.\n\n*This PR was created automatically by PR Review Agent*"
        }
    }
    
    /**
     * Extract PR URL from gh CLI output
     */
    private fun extractPRUrl(output: String): String? {
        val urlPattern = Regex("https://github\\.com/[^/]+/[^/]+/pull/\\d+")
        return urlPattern.find(output)?.value
    }
}

sealed class PRResult {
    data class Success(val prUrl: String) : PRResult()
    data class Failure(val error: String) : PRResult()
}

