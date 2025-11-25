package com.cmpagents.prreview.github

import com.cmpagents.prreview.models.RepositoryInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub CLI integration for authentication and operations
 */
class GitHubCLI {
    
    /**
     * Check if GitHub CLI is installed
     */
    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gh", "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if user is authenticated with GitHub CLI
     */
    suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gh", "auth", "status")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get authenticated user info
     */
    suspend fun getAuthenticatedUser(): GitHubUser? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gh", "api", "user")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Json.decodeFromString<GitHubUser>(output)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get GitHub token from CLI
     */
    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gh", "auth", "token")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val token = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && token.isNotEmpty()) {
                token
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Authenticate with GitHub CLI (opens browser)
     */
    suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gh", "auth", "login")
                .redirectErrorStream(true)
                .start()
            // This will open browser - user needs to complete authentication
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get repository info from current directory
     */
    suspend fun getRepositoryInfo(): RepositoryInfo? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gh", "repo", "view", "--json", "owner,name")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val repoData = Json.decodeFromString<GitHubRepo>(output)
                RepositoryInfo(
                    owner = repoData.owner.login,
                    repository = repoData.name,
                    provider = com.cmpagents.prreview.models.GitProviderType.GITHUB
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class GitHubRepo(
    val owner: GitHubRepoOwner,
    val name: String
)

@Serializable
data class GitHubRepoOwner(
    val login: String
)

