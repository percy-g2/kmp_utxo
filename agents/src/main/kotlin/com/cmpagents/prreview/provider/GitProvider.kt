package com.cmpagents.prreview.provider

import com.cmpagents.prreview.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Interface for fetching PR/MR details from git providers
 */
interface GitProvider {
    suspend fun fetchPRDetails(prInfo: PRInfo): PRDetails
    fun supports(provider: GitProviderType): Boolean
}

/**
 * GitHub API provider implementation
 */
class GitHubProvider(
    private val token: String? = null
) : GitProvider {
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    override fun supports(provider: GitProviderType): Boolean = provider == GitProviderType.GITHUB
    
    override suspend fun fetchPRDetails(prInfo: PRInfo): PRDetails {
        val url = "https://api.github.com/repos/${prInfo.owner}/${prInfo.repository}/pulls/${prInfo.number}"
        
        val response = client.get(url) {
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
            header("Accept", "application/vnd.github.v3+json")
        }
        
        if (!response.status.isSuccess()) {
            throw Exception("Failed to fetch PR details: ${response.status}")
        }
        
        val prData: GitHubPRResponse = response.body()
        
        // Fetch files changed
        val filesUrl = "$url/files"
        val filesResponse = client.get(filesUrl) {
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
            header("Accept", "application/vnd.github.v3+json")
        }
        
        val files: List<GitHubFileResponse> = filesResponse.body()
        
        // Fetch commits
        val commitsUrl = "$url/commits"
        val commitsResponse = client.get(commitsUrl) {
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
            header("Accept", "application/vnd.github.v3+json")
        }
        
        val commits: List<GitHubCommitResponse> = commitsResponse.body()
        
        return PRDetails(
            info = prInfo,
            title = prData.title,
            description = prData.body,
            author = prData.user.login,
            state = when (prData.state) {
                "open" -> PRState.OPEN
                "closed" -> if (prData.merged) PRState.MERGED else PRState.CLOSED
                "draft" -> PRState.DRAFT
                else -> PRState.OPEN
            },
            baseBranch = prData.base.ref,
            headBranch = prData.head.ref,
            files = files.map { file ->
                FileChange(
                    path = file.filename,
                    status = when (file.status) {
                        "added" -> FileStatus.ADDED
                        "removed" -> FileStatus.REMOVED
                        "renamed" -> FileStatus.RENAMED
                        else -> FileStatus.MODIFIED
                    },
                    additions = file.additions,
                    deletions = file.deletions,
                    changes = file.changes,
                    patch = file.patch
                )
            },
            commits = commits.map { commit ->
                CommitInfo(
                    sha = commit.sha.take(7),
                    message = commit.commit.message.split("\n").first(),
                    author = commit.commit.author.name,
                    date = commit.commit.author.date
                )
            },
            labels = prData.labels?.map { it.name } ?: emptyList()
        )
    }
}

/**
 * GitLab API provider implementation (placeholder)
 */
class GitLabProvider(
    private val token: String? = null
) : GitProvider {
    
    override fun supports(provider: GitProviderType): Boolean = provider == GitProviderType.GITLAB
    
    override suspend fun fetchPRDetails(prInfo: PRInfo): PRDetails {
        // TODO: Implement GitLab API integration
        throw NotImplementedError("GitLab provider not yet implemented")
    }
}

/**
 * Bitbucket API provider implementation (placeholder)
 */
class BitbucketProvider(
    private val token: String? = null
) : GitProvider {
    
    override fun supports(provider: GitProviderType): Boolean = provider == GitProviderType.BITBUCKET
    
    override suspend fun fetchPRDetails(prInfo: PRInfo): PRDetails {
        // TODO: Implement Bitbucket API integration
        throw NotImplementedError("Bitbucket provider not yet implemented")
    }
}

/**
 * Provider factory
 */
object GitProviderFactory {
    fun create(providerType: GitProviderType, token: String? = null): GitProvider {
        return when (providerType) {
            GitProviderType.GITHUB -> GitHubProvider(token)
            GitProviderType.GITLAB -> GitLabProvider(token)
            GitProviderType.BITBUCKET -> BitbucketProvider(token)
        }
    }
}

// GitHub API response models
@Serializable
private data class GitHubPRResponse(
    val title: String,
    val body: String?,
    val state: String,
    val merged: Boolean,
    val user: GitHubUser,
    val base: GitHubBranch,
    val head: GitHubBranch,
    val labels: List<GitHubLabel>? = null
)

@Serializable
private data class GitHubUser(
    val login: String
)

@Serializable
private data class GitHubBranch(
    val ref: String
)

@Serializable
private data class GitHubLabel(
    val name: String
)

@Serializable
private data class GitHubFileResponse(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null
)

@Serializable
private data class GitHubCommitResponse(
    val sha: String,
    val commit: GitHubCommit
)

@Serializable
private data class GitHubCommit(
    val message: String,
    val author: GitHubAuthor
)

@Serializable
private data class GitHubAuthor(
    val name: String,
    val date: String
)

