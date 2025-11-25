package com.cmpagents.prreview.parser

import com.cmpagents.prreview.models.GitProviderType
import com.cmpagents.prreview.models.PRInfo
import com.cmpagents.prreview.models.RepositoryInfo
import java.util.regex.Pattern

/**
 * Parses PR/MR URLs or numbers to extract repository and PR information
 */
object PRParser {
    
    /**
     * Parse PR/MR input (URL or number) and extract PR information
     */
    fun parse(input: String, repositoryInfo: RepositoryInfo? = null): PRInfo {
        return when {
            // Check if it's a URL
            input.startsWith("http://") || input.startsWith("https://") -> {
                parseURL(input)
            }
            // Check if it's just a number
            input.matches(Regex("\\d+")) -> {
                parseNumber(input.toInt(), repositoryInfo)
            }
            else -> {
                throw IllegalArgumentException("Invalid PR/MR input: $input. Expected URL or number.")
            }
        }
    }
    
    /**
     * Parse PR/MR URL
     */
    private fun parseURL(url: String): PRInfo {
        // GitHub: https://github.com/owner/repo/pull/123
        val githubPattern = Pattern.compile(
            "https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)/(?:pull|issues)/(\\d+)",
            Pattern.CASE_INSENSITIVE
        )
        val githubMatcher = githubPattern.matcher(url)
        if (githubMatcher.find()) {
            return PRInfo(
                provider = GitProviderType.GITHUB,
                owner = githubMatcher.group(1)!!,
                repository = githubMatcher.group(2)!!,
                number = githubMatcher.group(3)!!.toInt(),
                url = url
            )
        }
        
        // GitLab: https://gitlab.com/owner/repo/-/merge_requests/123
        val gitlabPattern = Pattern.compile(
            "https?://(?:www\\.)?gitlab\\.com/([^/]+)/([^/]+)/-/merge_requests/(\\d+)",
            Pattern.CASE_INSENSITIVE
        )
        val gitlabMatcher = gitlabPattern.matcher(url)
        if (gitlabMatcher.find()) {
            return PRInfo(
                provider = GitProviderType.GITLAB,
                owner = gitlabMatcher.group(1)!!,
                repository = gitlabMatcher.group(2)!!,
                number = gitlabMatcher.group(3)!!.toInt(),
                url = url
            )
        }
        
        // Bitbucket: https://bitbucket.org/owner/repo/pull-requests/123
        val bitbucketPattern = Pattern.compile(
            "https?://(?:www\\.)?bitbucket\\.org/([^/]+)/([^/]+)/pull-requests/(\\d+)",
            Pattern.CASE_INSENSITIVE
        )
        val bitbucketMatcher = bitbucketPattern.matcher(url)
        if (bitbucketMatcher.find()) {
            return PRInfo(
                provider = GitProviderType.BITBUCKET,
                owner = bitbucketMatcher.group(1)!!,
                repository = bitbucketMatcher.group(2)!!,
                number = bitbucketMatcher.group(3)!!.toInt(),
                url = url
            )
        }
        
        throw IllegalArgumentException("Unsupported PR/MR URL format: $url")
    }
    
    /**
     * Parse PR number with repository info
     */
    private fun parseNumber(number: Int, repositoryInfo: RepositoryInfo?): PRInfo {
        if (repositoryInfo == null) {
            throw IllegalArgumentException(
                "Repository information required when using PR number. " +
                "Please provide owner and repository, or use full PR URL."
            )
        }
        
        val provider = repositoryInfo.provider ?: GitProviderType.GITHUB // Default to GitHub
        
        return PRInfo(
            provider = provider,
            owner = repositoryInfo.owner,
            repository = repositoryInfo.repository,
            number = number,
            url = buildURL(provider, repositoryInfo.owner, repositoryInfo.repository, number)
        )
    }
    
    /**
     * Build PR URL from components
     */
    private fun buildURL(
        provider: GitProviderType,
        owner: String,
        repository: String,
        number: Int
    ): String {
        return when (provider) {
            GitProviderType.GITHUB -> "https://github.com/$owner/$repository/pull/$number"
            GitProviderType.GITLAB -> "https://gitlab.com/$owner/$repository/-/merge_requests/$number"
            GitProviderType.BITBUCKET -> "https://bitbucket.org/$owner/$repository/pull-requests/$number"
        }
    }
    
    /**
     * Extract repository info from git remote URL
     */
    fun extractRepositoryFromGitRemote(remoteUrl: String): RepositoryInfo? {
        // SSH: git@github.com:owner/repo.git
        val sshPattern = Pattern.compile("git@(?:github|gitlab)\\.com:([^/]+)/([^/]+)(?:\\.git)?")
        val sshMatcher = sshPattern.matcher(remoteUrl)
        if (sshMatcher.find()) {
            val host = if (remoteUrl.contains("gitlab")) "gitlab.com" else "github.com"
            return RepositoryInfo(
                owner = sshMatcher.group(1)!!,
                repository = sshMatcher.group(2)!!.removeSuffix(".git"),
                provider = if (host.contains("gitlab")) GitProviderType.GITLAB else GitProviderType.GITHUB
            )
        }
        
        // HTTPS: https://github.com/owner/repo.git
        val httpsPattern = Pattern.compile("https?://(?:www\\.)?(github|gitlab)\\.com/([^/]+)/([^/]+)(?:\\.git)?")
        val httpsMatcher = httpsPattern.matcher(remoteUrl)
        if (httpsMatcher.find()) {
            val provider = if (httpsMatcher.group(1) == "gitlab") {
                GitProviderType.GITLAB
            } else {
                GitProviderType.GITHUB
            }
            return RepositoryInfo(
                owner = httpsMatcher.group(2)!!,
                repository = httpsMatcher.group(3)!!.removeSuffix(".git"),
                provider = provider
            )
        }
        
        return null
    }
}

