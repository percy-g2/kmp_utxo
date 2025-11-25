package com.cmpagents.prreview.models

/**
 * Parsed PR/MR information
 */
data class PRInfo(
    val provider: GitProviderType,
    val owner: String,
    val repository: String,
    val number: Int,
    val url: String
)

enum class GitProviderType {
    GITHUB, GITLAB, BITBUCKET
}

/**
 * Repository information
 */
data class RepositoryInfo(
    val owner: String,
    val repository: String,
    val provider: GitProviderType? = null
)

/**
 * Detailed PR/MR information
 */
data class PRDetails(
    val info: PRInfo,
    val title: String,
    val description: String?,
    val author: String,
    val state: PRState,
    val baseBranch: String,
    val headBranch: String,
    val files: List<FileChange>,
    val commits: List<CommitInfo>,
    val labels: List<String> = emptyList(),
    val reviewers: List<String> = emptyList()
)

enum class PRState {
    OPEN, CLOSED, MERGED, DRAFT
}

/**
 * File change information
 */
data class FileChange(
    val path: String,
    val status: FileStatus,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null,
    val diff: String? = null
)

enum class FileStatus {
    ADDED, MODIFIED, REMOVED, RENAMED
}

/**
 * Commit information
 */
data class CommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String
)

/**
 * Code analysis result
 */
data class CodeAnalysis(
    val kmpIssues: List<KMPIssue>,
    val codeQualityIssues: List<CodeQualityIssue>,
    val securityIssues: List<SecurityIssue>,
    val testCoverage: Double? = null,
    val affectedPlatforms: List<String> = emptyList(),
    val affectedSourceSets: List<String> = emptyList()
)

/**
 * KMP-specific issue
 */
data class KMPIssue(
    val file: String,
    val line: Int?,
    val type: KMPIssueType,
    val message: String,
    val suggestion: String? = null
)

enum class KMPIssueType {
    EXPECT_ACTUAL_MISMATCH,
    PLATFORM_LEAK,
    COMMON_DEPENDENCY_VIOLATION,
    MISSING_PLATFORM_IMPLEMENTATION,
    ARCHITECTURE_VIOLATION
}

/**
 * Code quality issue
 */
data class CodeQualityIssue(
    val file: String,
    val line: Int?,
    val type: QualityIssueType,
    val message: String,
    val suggestion: String? = null
)

enum class QualityIssueType {
    COMPLEXITY, DUPLICATION, NAMING, STYLE, PERFORMANCE
}

/**
 * Security issue
 */
data class SecurityIssue(
    val file: String,
    val line: Int?,
    val type: SecurityIssueType,
    val message: String,
    val severity: SecuritySeverity,
    val suggestion: String? = null
)

enum class SecurityIssueType {
    HARDCODED_SECRET, SQL_INJECTION, XSS, INSECURE_API, PERMISSION_ISSUE
}

enum class SecuritySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Review comment
 */
data class ReviewComment(
    val file: String,
    val line: Int?,
    val severity: ReviewSeverity,
    val message: String,
    val suggestion: String? = null,
    val codeSnippet: String? = null,
    val category: ReviewCategory
)

enum class ReviewSeverity {
    CRITICAL, WARNING, SUGGESTION, INFO
}

enum class ReviewCategory {
    KMP_ARCHITECTURE,
    CODE_QUALITY,
    SECURITY,
    PERFORMANCE,
    TESTING,
    DOCUMENTATION,
    STYLE
}

