package com.cmpagents.prreview.rules

import com.cmpagents.prreview.models.*
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rulebook loader and parser for PR review rules
 */
class Rulebook(private val rulebookPath: String = "agents/rules/pr-review.md") {
    
    private val rules: RulebookContent by lazy {
        loadRulebook()
    }
    
    /**
     * Load and parse the rulebook
     */
    fun loadRulebook(): RulebookContent {
        val rulebookFile = File(rulebookPath)
        if (!rulebookFile.exists()) {
            // Try alternative paths
            val altPaths = listOf(
                ".cmp-agents/rules/pr-review.md",
                "rules/pr-review.md",
                "../agents/rules/pr-review.md"
            )
            val foundFile = altPaths.firstOrNull { File(it).exists() }
            if (foundFile != null) {
                return parseRulebook(File(foundFile).readText())
            }
            return RulebookContent() // Return default rules
        }
        return parseRulebook(rulebookFile.readText())
    }
    
    /**
     * Parse markdown rulebook into structured rules
     */
    private fun parseRulebook(content: String): RulebookContent {
        // Extract rules from markdown
        val rules = RulebookContent()
        
        // Parse KMP Architecture Rules
        rules.kmpRules = parseKMPRules(content)
        
        // Parse Code Quality Rules
        rules.codeQualityRules = parseCodeQualityRules(content)
        
        // Parse Security Rules
        rules.securityRules = parseSecurityRules(content)
        
        // Parse Testing Rules
        rules.testingRules = parseTestingRules(content)
        
        // Parse Documentation Rules
        rules.documentationRules = parseDocumentationRules(content)
        
        return rules
    }
    
    /**
     * Get severity for a specific issue type
     */
    fun getSeverityForIssue(issueType: KMPIssueType): ReviewSeverity {
        return when (issueType) {
            KMPIssueType.EXPECT_ACTUAL_MISMATCH,
            KMPIssueType.COMMON_DEPENDENCY_VIOLATION,
            KMPIssueType.PLATFORM_LEAK -> ReviewSeverity.CRITICAL
            KMPIssueType.ARCHITECTURE_VIOLATION -> ReviewSeverity.WARNING
            KMPIssueType.MISSING_PLATFORM_IMPLEMENTATION -> ReviewSeverity.WARNING
        }
    }
    
    /**
     * Get severity for security issues
     */
    fun getSeverityForSecurity(securityType: SecurityIssueType): ReviewSeverity {
        return when (securityType) {
            SecurityIssueType.HARDCODED_SECRET -> ReviewSeverity.CRITICAL
            SecurityIssueType.SQL_INJECTION,
            SecurityIssueType.XSS -> ReviewSeverity.CRITICAL
            SecurityIssueType.INSECURE_API -> ReviewSeverity.WARNING
            SecurityIssueType.PERMISSION_ISSUE -> ReviewSeverity.WARNING
        }
    }
    
    /**
     * Check if function length violates rules
     */
    fun checkFunctionLength(lines: Int): ReviewSeverity? {
        return when {
            lines >= 100 -> ReviewSeverity.CRITICAL
            lines >= 50 -> ReviewSeverity.WARNING
            lines >= 30 -> ReviewSeverity.SUGGESTION
            else -> null
        }
    }
    
    /**
     * Check if test coverage is required
     */
    fun requiresTests(fileCount: Int): Boolean {
        return fileCount >= 3 // Rule 11: 3+ files changed should have tests
    }
    
    /**
     * Check if documentation is required
     */
    fun requiresDocumentation(hasPublicAPI: Boolean, isSignificantChange: Boolean): Boolean {
        return hasPublicAPI || isSignificantChange
    }
    
    /**
     * Get platform leak patterns to check
     */
    fun getPlatformLeakPatterns(): List<String> {
        return listOf(
            "java.io",
            "java.net",
            "androidx.compose.ui.platform",
            "kotlinx.cinterop",
            "platform.Foundation",
            "platform.UIKit"
        )
    }
    
    /**
     * Get secret patterns to check
     */
    fun getSecretPatterns(): List<Regex> {
        return listOf(
            Regex("(api[_-]?key|apikey)\\s*[=:]\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE),
            Regex("(password|passwd|pwd)\\s*[=:]\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE),
            Regex("(secret|token)\\s*[=:]\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        )
    }
    
    /**
     * Get platform-specific dependencies to check
     */
    fun getPlatformSpecificDependencies(): List<String> {
        return listOf(
            "io.ktor:ktor-client-android",
            "io.ktor:ktor-client-darwin",
            "io.ktor:ktor-client-cio",
            "androidx.compose.ui.platform"
        )
    }
    
    private fun parseKMPRules(content: String): KMPRules {
        // Extract KMP rules section
        val kmpSection = extractSection(content, "KMP/CMP Architecture Rules")
        return KMPRules(
            validateExpectActual = kmpSection.contains("Rule 1"),
            checkPlatformLeaks = kmpSection.contains("Rule 2"),
            verifyDependencies = kmpSection.contains("Rule 3"),
            checkSourceSetOrganization = kmpSection.contains("Rule 4")
        )
    }
    
    private fun parseCodeQualityRules(content: String): CodeQualityRules {
        val qualitySection = extractSection(content, "Code Quality Standards")
        return CodeQualityRules(
            maxFunctionLength = extractMaxFunctionLength(qualitySection),
            checkDuplication = qualitySection.contains("Rule 6"),
            enforceNaming = qualitySection.contains("Rule 7"),
            requireErrorHandling = qualitySection.contains("Rule 8")
        )
    }
    
    private fun parseSecurityRules(content: String): SecurityRules {
        val securitySection = extractSection(content, "Security Guidelines")
        return SecurityRules(
            checkHardcodedSecrets = securitySection.contains("Rule 9"),
            checkInsecureAPIs = securitySection.contains("Rule 10")
        )
    }
    
    private fun parseTestingRules(content: String): TestingRules {
        val testingSection = extractSection(content, "Testing Requirements")
        return TestingRules(
            requireTestsForNewFeatures = testingSection.contains("Rule 11"),
            minFilesForTestRequirement = 3
        )
    }
    
    private fun parseDocumentationRules(content: String): DocumentationRules {
        val docSection = extractSection(content, "Documentation Standards")
        return DocumentationRules(
            requirePublicAPIDocs = docSection.contains("Rule 13"),
            requireReadmeUpdates = docSection.contains("Rule 14")
        )
    }
    
    private fun extractSection(content: String, sectionTitle: String): String {
        val startMarker = "## $sectionTitle"
        val startIndex = content.indexOf(startMarker)
        if (startIndex == -1) return ""
        
        val nextSectionIndex = content.indexOf("\n## ", startIndex + startMarker.length)
        val endIndex = if (nextSectionIndex == -1) content.length else nextSectionIndex
        
        return content.substring(startIndex, endIndex)
    }
    
    private fun extractMaxFunctionLength(section: String): Int {
        val pattern = Regex("(\\d+)\\s+lines")
        val match = pattern.find(section)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 50
    }
}

/**
 * Structured representation of rulebook content
 */
@Serializable
data class RulebookContent(
    val kmpRules: KMPRules = KMPRules(),
    val codeQualityRules: CodeQualityRules = CodeQualityRules(),
    val securityRules: SecurityRules = SecurityRules(),
    val testingRules: TestingRules = TestingRules(),
    val documentationRules: DocumentationRules = DocumentationRules()
)

@Serializable
data class KMPRules(
    val validateExpectActual: Boolean = true,
    val checkPlatformLeaks: Boolean = true,
    val verifyDependencies: Boolean = true,
    val checkSourceSetOrganization: Boolean = true
)

@Serializable
data class CodeQualityRules(
    val maxFunctionLength: Int = 50,
    val checkDuplication: Boolean = true,
    val enforceNaming: Boolean = true,
    val requireErrorHandling: Boolean = true
)

@Serializable
data class SecurityRules(
    val checkHardcodedSecrets: Boolean = true,
    val checkInsecureAPIs: Boolean = true
)

@Serializable
data class TestingRules(
    val requireTestsForNewFeatures: Boolean = true,
    val minFilesForTestRequirement: Int = 3
)

@Serializable
data class DocumentationRules(
    val requirePublicAPIDocs: Boolean = true,
    val requireReadmeUpdates: Boolean = false
)

