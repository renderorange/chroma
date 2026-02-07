# Agent Personal Instructions

## Automated Session File Reading (ENFORCED)

**SYSTEM HOOK**: Before processing any user request, this system automatically:
1. Detects if AGENTS.md exists in project root
2. Detects if AGENTS_SESSION.md exists in project root
3. Reads both files and loads their content as session context
4. Provides this context to the agent before any tool execution

**VALIDATION**: The system blocks execution of test-related tools until session files are loaded.

## Operating Principles

1. **Confirmation Required**: Never commit changes or run commands without explicit user confirmation first. Always ask before:
   - Running `git commit`, `git push`, or any git commands that modify the repository
   - Executing build, test, or deployment commands
   - Making any system-level changes

2. **Commit Message Standards**:
   - Never add `Co-authored-by:` or similar attribution lines to commit messages
   - Never add git task types (e.g., "feat:", "fix:", "docs:", "test:", "chore:") to commit messages
   - Never use work-in-progress markers like "WIP:" or "TODO:" in commit messages
   - Commit messages should be clean, descriptive sentences focused on the change
   - Start commit messages with capital letters (proper sentence capitalization)

3. **Agent Skills Frameworks**: Ensure the following skills frameworks are installed and being used:
    - [superpowers](https://github.com/obra/superpowers)

4. **Documentation First**: Always read all project documentation before starting any work session:
   - Read AGENTS.md file first if it exists
   - Read `AGENTS_SESSION.md` and the project files listed within
   - Read `Current Status` for tracking where to continue in the project
   - Check for README.md, package.json, or other config files
   - Look for existing patterns and conventions in the codebase

5. **Documentation Updates**: Always update project documentation to track progress:
    - Update AGENTS.md when adding new general patterns or discovering issues
    - Document any model limitations or compatibility problems
    - Note successful workflows and command sequences
    - Progress plans must be documented and updated into files in `docs/plans/`
    - **Always keep AGENTS_SESSION.md current with the latest project status after any changes**

## Session Workflow

1. **Start**: Read AGENTS.md and `AGENTS_SESSION.md` at the start of each session
   - **MANDATORY**: Always read these files first before ANY other work
   - AGENTS.md contains project guidelines and test locations
   - AGENTS_SESSION.md contains current status and test commands
   - **SYSTEM ENFORCEMENT**: This reading is enforced by automatic hooks and validation
2. **Plan**: Ask for confirmation before making changes
3. **Execute**: Follow established patterns and document deviations
4. **Update**: Add findings and progress to files in `docs/plans/` and `AGENTS_SESSION.md`
5. **Review**: Verify documentation accurately reflects current state

## Automated Workflow Enforcement

**TECHNICAL IMPLEMENTATION**: The following mechanisms enforce session workflow compliance:

### 1. Pre-Response Hook (Automatic Session File Reading)
Before responding to any user request, the system automatically:
- Checks for AGENTS.md in project root
- Checks for AGENTS_SESSION.md in project root
- Reads both files if they exist
- Provides session context to the agent before tool execution

### 2. Test Request Detection and Session File Enforcement
For any request containing test-related keywords:
- "test", "tests", "testing", "run.*test", "test.*suite"
- System forces reading of AGENTS_SESSION.md first
- Blocks test-related tool use until session files are read
- Provides test commands from AGENTS_SESSION.md automatically

### 3. Session File Validation Before Tool Use
Before executing test-related tools:
- bash: validation that AGENTS_SESSION.md has been read
- sclang: validation that session context is loaded
- go test: validation that test locations are known
- System blocks tool use if session files not read with error: "Must read AGENTS_SESSION.md first for test execution"

**ENFORCEMENT TRIGGERS**:
- User message contains test keywords
- Tool execution attempts test-related operations
- Any bash command with "test", "go test", "sclang"
- Any work in chroma-tui directory with test operations

## General Code Style Guidelines

### General Principles

1. **Clarity over cleverness**: Write code that is easy to understand
2. **Consistency**: Follow established patterns within the codebase
3. **Documentation**: Document complex logic and API boundaries
4. **Error handling**: Handle errors gracefully and provide meaningful messages

### Import Organization

When working with JavaScript/TypeScript:
```javascript
// Node.js built-ins
import fs from 'fs';
import path from 'path';

// External dependencies
import express from 'express';
import _ from 'lodash';

// Internal modules
import { utils } from './utils.js';
import { config } from '../config/index.js';
```

### Naming Conventions

- **Variables and functions**: `snake_case`
- **Constants**: `UPPER_SNAKE_CASE`
- **Classes**: `PascalCase`
- **Files**: `snake_case.js` or `snake_case.ts`
- **Directories**: `snake_case`

### Error Handling

```javascript
// Prefer specific error types
class ValidationError extends Error {
  constructor(message, field) {
    super(message);
    this.name = 'ValidationError';
    this.field = field;
  }
}

// Handle errors at boundaries
try {
  const result = await riskyOperation();
  return result;
} catch (error) {
  if (error instanceof ValidationError) {
    logger.warn('Validation failed', { field: error.field });
    throw error;
  }
  logger.error('Unexpected error', { error: error.message });
  throw new Error('Operation failed');
}
```

### Code Formatting
- Use 2 spaces for indentation
- Maximum line length: 80 characters
- Use single quotes for strings
- Include trailing commas in multi-line structures
- **Never add trailing whitespace at the end of lines**

### Git Workflow
#### Commit Message Format
```
description

optional detailed explanation
```

### Testing Strategy

- **Unit Testing**: Favor unit testing with mocking unless specifically indicated otherwise
- Test individual components in isolation
- Mock external dependencies and services
- Focus on testing business logic and edge cases
- Use descriptive test names that explain the behavior being tested

**TEST EXECUTION VALIDATION**: 
- System validates AGENTS_SESSION.md has been read before allowing test execution
- Test commands are automatically loaded from session context
- Manual test command discovery is prohibited to prevent wasted tokens

### Running Tests

**IMPORTANT: Always run tests from project root directory**

```bash
# Primary test suites (run both for complete verification)
./functional_full_workflow.sh     # SuperCollider synth tests + basic integration
./integration_tui_osc.sh          # Go TUI + OSC communication tests

# Individual test files
./test_syntax.sh                  # Syntax validation only
./test_compilation.sh             # SuperCollider compilation test
./test_headless.sh                # Headless server boot test

# SuperCollider tests directly
sclang test_synths.scd           # Full effects workflow test
sclang test_pronounced_mode.scd  # Grain intensity test (simple)

# Go unit tests (from chroma-tui directory)
cd chroma-tui && go test ./...   # All Go tests
cd chroma-tui && go test -v ./integration/  # OSC integration tests
cd chroma-tui && go test -v ./functional/   # Full workflow tests
```

**Test Location Reference:**
- Functional tests: `functional_full_workflow.sh` (runs SuperCollider tests)
- Integration tests: `integration_tui_osc.sh` (runs Go tests + SC integration)
- Individual SC tests: `test_synths.scd`, `test_pronounced_mode.scd`
- Go test directories: `chroma-tui/integration/`, `chroma-tui/functional/`, `chroma-tui/tui/`, `chroma-tui/osc/`

### Security Considerations

- Never commit API keys or sensitive configuration
- Use environment variables for secrets
- Validate all external inputs
- Follow principle of least privilege

Remember: Prioritize thorough testing, clear documentation, and always respect user confirmation requirements.
