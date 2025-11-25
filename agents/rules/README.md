# PR Review Rulebook

This directory contains the **AI Agent Rulebook** - a comprehensive set of rules that all AI agents follow when reviewing code.

## Purpose

The rulebook ensures:
- ✅ **Consistent Reviews**: All agents follow the same standards
- ✅ **Transparency**: Review criteria are clearly documented
- ✅ **Customization**: Teams can adapt rules to their needs
- ✅ **Quality**: Enforces best practices for KMP/CMP development

## Main Rulebook

**`pr-review.md`** - The primary rulebook that defines:
- KMP/CMP architecture rules
- Code quality standards
- Security guidelines
- Testing requirements
- Documentation standards
- Review severity levels

## How It Works

1. **Agent Loads Rulebook**: When the PR Review Agent starts, it loads `pr-review.md`
2. **Rules Are Parsed**: The rulebook is parsed into structured rules
3. **Code Is Analyzed**: Code changes are checked against the rules
4. **Comments Are Generated**: Review comments reference specific rules

## Customizing Rules

### For Your Team

1. **Edit the Rulebook**: Modify `pr-review.md` to match your team's standards
2. **Adjust Severity Levels**: Change what constitutes CRITICAL vs WARNING
3. **Add Custom Rules**: Add project-specific patterns to check
4. **Update Thresholds**: Modify function length limits, test requirements, etc.

### Example Customization

```markdown
### Rule 5: Function Complexity
**Severity: SUGGESTION**

- **SHOULD NOT**: Functions exceed 30 lines of code  <!-- Changed from 50 -->
- **SHOULD**: Break down complex functions into smaller, focused functions
```

The agent will automatically use your custom thresholds.

## Rule Structure

Each rule follows this structure:

```markdown
### Rule N: Rule Name
**Severity: LEVEL**

- **MUST/MUST NOT**: Required behaviors
- **SHOULD/SHOULD NOT**: Recommended behaviors
- **MAY**: Optional behaviors

**Example Violation:**
```kotlin
// Bad code
```

**Correct Pattern:**
```kotlin
// Good code
```
```

## Severity Levels

- **CRITICAL**: Must be fixed before merge (security, architecture violations)
- **WARNING**: Should be addressed (code quality, potential bugs)
- **SUGGESTION**: May be addressed (style, refactoring opportunities)
- **INFO**: Informational (best practices, reminders)

## Rule Categories

### KMP/CMP Architecture (Rules 1-4)
- Expect/actual pattern validation
- Platform leak prevention
- Dependency management
- Source set organization

### Code Quality (Rules 5-8)
- Function complexity
- Code duplication
- Naming conventions
- Error handling

### Security (Rules 9-10)
- Hardcoded secrets
- Insecure APIs

### Testing (Rules 11-12)
- Test coverage
- Test organization

### Documentation (Rules 13-14)
- Public API documentation
- README and changelog updates

## Integration

The rulebook is automatically loaded by:
- PR Review Agent CLI
- CI/CD pipelines (when integrated)
- IDE plugins (when available)

## Version Control

The rulebook should be:
- ✅ Committed to version control
- ✅ Reviewed and updated regularly
- ✅ Shared across the team
- ✅ Referenced in PR reviews

## Best Practices

1. **Start Conservative**: Begin with strict rules, relax as needed
2. **Document Rationale**: Explain why rules exist
3. **Review Regularly**: Update rules based on team feedback
4. **Be Specific**: Provide clear examples of violations and fixes
5. **Balance**: Don't create too many rules - focus on what matters

## Contributing

When adding new rules:
1. Follow the existing rule structure
2. Provide clear examples
3. Specify severity level
4. Update this README if adding new categories

## Questions?

See the main [README.md](../README.md) or [USAGE.md](../USAGE.md) for more information about using the PR Review Agent.

