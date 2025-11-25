package com.cmpagents.prreview.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pushes changes to GitHub
 */
class ChangePusher {
    
    /**
     * Stage all changes
     */
    suspend fun stageAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("git", "add", ".")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Stage specific files
     */
    suspend fun stageFiles(files: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("git", "add")
            command.addAll(files)
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Commit changes
     */
    suspend fun commit(message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("git", "commit", "-m", message)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Push changes to remote
     */
    suspend fun push(branch: String? = null, force: Boolean = false): PushResult = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("git", "push")
            
            if (force) {
                command.add("--force")
            }
            
            if (branch != null) {
                command.add("origin")
                command.add(branch)
            }
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                PushResult.Success(output)
            } else {
                PushResult.Failure("Failed to push: $output")
            }
        } catch (e: Exception) {
            PushResult.Failure("Error pushing changes: ${e.message}")
        }
    }
    
    /**
     * Create a new branch
     */
    suspend fun createBranch(branchName: String, fromBranch: String = "main"): Boolean = withContext(Dispatchers.IO) {
        try {
            // First checkout the base branch
            val checkoutProcess = ProcessBuilder("git", "checkout", fromBranch)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            checkoutProcess.waitFor()
            
            // Create and checkout new branch
            val branchProcess = ProcessBuilder("git", "checkout", "-b", branchName)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            branchProcess.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Stage, commit, and push changes in one operation
     */
    suspend fun stageCommitAndPush(
        message: String,
        branch: String? = null,
        files: List<String>? = null
    ): PushResult = withContext(Dispatchers.IO) {
        try {
            // Stage files
            val staged = if (files != null) {
                stageFiles(files)
            } else {
                stageAll()
            }
            
            if (!staged) {
                return@withContext PushResult.Failure("Failed to stage files")
            }
            
            // Commit
            val committed = commit(message)
            if (!committed) {
                return@withContext PushResult.Failure("Failed to commit changes")
            }
            
            // Push
            push(branch)
        } catch (e: Exception) {
            PushResult.Failure("Error in stage-commit-push: ${e.message}")
        }
    }
    
    /**
     * Get current branch name
     */
    suspend fun getCurrentBranch(): String? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("git", "branch", "--show-current")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val branch = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && branch.isNotEmpty()) {
                branch
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if there are uncommitted changes
     */
    suspend fun hasUncommittedChanges(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            output.trim().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

sealed class PushResult {
    data class Success(val output: String) : PushResult()
    data class Failure(val error: String) : PushResult()
}

