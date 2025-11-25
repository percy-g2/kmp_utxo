package com.cmpagents.prreview.analyzer

import com.cmpagents.prreview.models.*
import com.cmpagents.prreview.rules.Rulebook
import java.io.File

/**
 * Analyzes code changes for KMP-specific issues and code quality problems
 * Follows rules defined in the PR review rulebook
 */
class CodeAnalyzer(
    private val rulebook: Rulebook = Rulebook()
) {
    
    fun analyze(prDetails: PRDetails): CodeAnalysis {
        val kmpIssues = mutableListOf<KMPIssue>()
        val codeQualityIssues = mutableListOf<CodeQualityIssue>()
        val securityIssues = mutableListOf<SecurityIssue>()
        val affectedSourceSets = mutableSetOf<String>()
        val affectedPlatforms = mutableSetOf<String>()
        
        prDetails.files.forEach { fileChange ->
            // Detect affected source sets
            detectSourceSets(fileChange.path, affectedSourceSets, affectedPlatforms)
            
            // Analyze KMP-specific issues
            if (fileChange.patch != null) {
                kmpIssues.addAll(analyzeKMPIssues(fileChange))
            }
            
            // Analyze code quality
            if (fileChange.patch != null) {
                codeQualityIssues.addAll(analyzeCodeQuality(fileChange))
            }
            
            // Analyze security issues
            if (fileChange.patch != null) {
                securityIssues.addAll(analyzeSecurity(fileChange))
            }
        }
        
        return CodeAnalysis(
            kmpIssues = kmpIssues,
            codeQualityIssues = codeQualityIssues,
            securityIssues = securityIssues,
            affectedPlatforms = affectedPlatforms.toList(),
            affectedSourceSets = affectedSourceSets.toList()
        )
    }
    
    private fun detectSourceSets(
        filePath: String,
        sourceSets: MutableSet<String>,
        platforms: MutableSet<String>
    ) {
        // Detect source sets from path
        val sourceSetPattern = Regex("src/(\\w+Main)/")
        val match = sourceSetPattern.find(filePath)
        if (match != null) {
            val sourceSet = match.groupValues[1]
            sourceSets.add(sourceSet)
            
            // Map source set to platform
            when (sourceSet) {
                "commonMain" -> platforms.add("All Platforms")
                "androidMain" -> platforms.add("Android")
                "iosMain" -> platforms.add("iOS")
                "desktopMain" -> platforms.add("Desktop")
                "webMain", "wasmJsMain" -> platforms.add("Web")
            }
        }
    }
    
    private fun analyzeKMPIssues(fileChange: FileChange): List<KMPIssue> {
        val issues = mutableListOf<KMPIssue>()
        val patch = fileChange.patch ?: return issues
        
        // Check for expect declarations without actual implementations
        if (patch.contains("expect ") && fileChange.path.contains("commonMain")) {
            val expectPattern = Regex("expect\\s+(fun|class|interface|typealias)\\s+(\\w+)")
            expectPattern.findAll(patch).forEach { match ->
                val expectName = match.groupValues[2]
                // Check if actual exists in platform sources
                if (!hasMatchingActual(fileChange.path, expectName)) {
                    issues.add(
                        KMPIssue(
                            file = fileChange.path,
                            line = null,
                            type = KMPIssueType.MISSING_PLATFORM_IMPLEMENTATION,
                            message = "Expect declaration '$expectName' may be missing actual implementations in platform source sets",
                            suggestion = "Verify that actual implementations exist in androidMain, iosMain, etc."
                        )
                    )
                }
            }
        }
        
        // Check for platform-specific code in commonMain (following rulebook)
        if (fileChange.path.contains("commonMain") && rulebook.loadRulebook().kmpRules.checkPlatformLeaks) {
            val platformImports = rulebook.getPlatformLeakPatterns()
            
            platformImports.forEach { import ->
                if (patch.contains(import)) {
                    issues.add(
                        KMPIssue(
                            file = fileChange.path,
                            line = null,
                            type = KMPIssueType.PLATFORM_LEAK,
                            message = "Platform-specific import '$import' detected in commonMain (violates Rule 2: Platform Leak Prevention)",
                            suggestion = "Move platform-specific code to expect/actual pattern as per PR review rulebook"
                        )
                    )
                }
            }
        }
        
        // Check for platform-specific dependencies in commonMain build files (following rulebook)
        if (fileChange.path.contains("build.gradle.kts") && fileChange.path.contains("common") 
            && rulebook.loadRulebook().kmpRules.verifyDependencies) {
            val platformDeps = rulebook.getPlatformSpecificDependencies()
            
            platformDeps.forEach { dep ->
                if (patch.contains(dep)) {
                    issues.add(
                        KMPIssue(
                            file = fileChange.path,
                            line = null,
                            type = KMPIssueType.COMMON_DEPENDENCY_VIOLATION,
                            message = "Platform-specific dependency '$dep' found in common source set (violates Rule 3: Dependency Management)",
                            suggestion = "Move dependency to platform-specific source set (androidMain, iosMain, etc.) as per PR review rulebook"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    private fun analyzeCodeQuality(fileChange: FileChange): List<CodeQualityIssue> {
        val issues = mutableListOf<CodeQualityIssue>()
        val patch = fileChange.patch ?: return issues
        
        // Check for long functions (simple heuristic)
        val lines = patch.lines()
        var functionStart = -1
        var braceCount = 0
        
        lines.forEachIndexed { index, line ->
            if (line.contains("fun ") && functionStart == -1) {
                functionStart = index
                braceCount = line.count { it == '{' } - line.count { it == '}' }
            } else if (functionStart != -1) {
                braceCount += line.count { it == '{' } - line.count { it == '}' }
                if (braceCount == 0 && functionStart != -1) {
                    val functionLength = index - functionStart
                    val severity = rulebook.checkFunctionLength(functionLength)
                    if (severity != null) {
                        issues.add(
                            CodeQualityIssue(
                                file = fileChange.path,
                                line = null,
                                type = QualityIssueType.COMPLEXITY,
                                message = "Long function detected (~$functionLength lines) violates Rule 5: Function Complexity. " +
                                        "Functions should not exceed ${rulebook.loadRulebook().codeQualityRules.maxFunctionLength} lines.",
                                suggestion = "Extract smaller functions for better readability and testability as per PR review rulebook"
                            )
                        )
                    }
                    functionStart = -1
                }
            }
        }
        
        return issues
    }
    
    private fun analyzeSecurity(fileChange: FileChange): List<SecurityIssue> {
        val issues = mutableListOf<SecurityIssue>()
        val patch = fileChange.patch ?: return issues
        
        // Check for hardcoded secrets (following rulebook Rule 9)
        if (rulebook.loadRulebook().securityRules.checkHardcodedSecrets) {
            val secretPatterns = rulebook.getSecretPatterns()
            
            secretPatterns.forEach { pattern ->
                pattern.findAll(patch).forEach { match ->
                    issues.add(
                        SecurityIssue(
                            file = fileChange.path,
                            line = null,
                            type = SecurityIssueType.HARDCODED_SECRET,
                            message = "Potential hardcoded secret detected: ${match.groupValues[1]} (violates Rule 9: Hardcoded Secrets)",
                            severity = SecuritySeverity.HIGH,
                            suggestion = "Use environment variables or secure configuration management as per PR review rulebook"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    private fun hasMatchingActual(filePath: String, expectName: String): Boolean {
        // This is a simplified check - in a real implementation,
        // you would search through the repository for actual declarations
        // For now, we'll assume they exist if the file is in commonMain
        return true // Placeholder
    }
}

