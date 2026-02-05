# Agent Personal Instructions

## Operating Principles

1. **Confirmation Required**: Never commit changes or run commands without explicit user confirmation first. Always ask before:
   - Running `git commit`, `git push`, or any git commands that modify the repository
   - Executing build, test, or deployment commands
   - Making any system-level changes

2. **Commit Message Standards**: Never add `Co-authored-by:` or similar attribution lines to commit messages. Commit messages should be clean and focused on the change description only.

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

## Session Workflow

1. **Start**: Read AGENTS.md and `AGENTS_SESSION.md` at the start of each session
2. **Plan**: Ask for confirmation before making changes
3. **Execute**: Follow established patterns and document deviations
4. **Update**: Add findings and progress to files in `docs/plans/` and `AGENTS_SESSION.md`
5. **Review**: Verify documentation accurately reflects current state

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

### Security Considerations

- Never commit API keys or sensitive configuration
- Use environment variables for secrets
- Validate all external inputs
- Follow principle of least privilege

Remember: Prioritize thorough testing, clear documentation, and always respect user confirmation requirements.
