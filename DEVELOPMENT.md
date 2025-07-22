# HoneySQL Development Guidelines

This document captures development practices, testing procedures, and code structure guidelines for HoneySQL based on real implementation experience.

## Testing & Validation

### Running Tests

HoneySQL has multiple test suites that should all pass before committing changes:

```bash
# Run unit tests
clojure -X:test

# Run documentation tests (validates all code examples in docs)
clojure -T:build run-doc-tests

# Run full CI pipeline (comprehensive testing)
clojure -T:build ci
```

**Important**: Always run documentation tests after making changes to documentation files. The `run-doc-tests` command:
- Parses all `.md` files in `doc/` and `README.md`
- Extracts Clojure code blocks
- Generates executable test files
- Runs them to ensure examples work correctly

### REPL-Driven Development

Use Calva's REPL for iterative development:

```clojure
;; Start by requiring the namespace
(require '[honey.sql :as sql])
(require '[honey.sql.pg-ops :as pg])

;; Test your changes interactively
(sql/format {:select [[[:make_interval [:=> :secs 10]]]]})
;; => ["SELECT MAKE_INTERVAL(secs => ?)" 10]
```

**Best Practice**: Validate each change in the REPL before writing tests.

## Code Structure Guidelines

### Operator Implementation Pattern

When adding new operators (like the `=>` operator), follow this established pattern:

1. **Definition** (`src/honey/sql/pg_ops.cljc`):
```clojure
(def => "The => operator for PostgreSQL named parameters in function calls." :=>)
```

2. **Registration**:
```clojure
(sql/register-op! =>)
```

3. **Testing** (`test/honey/sql/pg_ops_test.cljc`):
```clojure
(testing "named parameter operator"
  (is (= ["SELECT MAKE_INTERVAL(secs => ?)" 10]
         (sql/format {:select [[[:make_interval [:=> :secs 10]]]]})))
  ;; ... more test cases
  )
```

### Documentation Pattern

When documenting new features in `doc/postgresql.md`:

1. **Clear Section Headers**: Use `##` for major features
2. **Require Statement**: Show the require with specific imports
3. **Progressive Examples**: Start simple, build complexity
4. **Real Use Cases**: Use actual PostgreSQL functions, not abstract examples

Example structure:
```markdown
## Feature Name

Brief description of what it does and why it's useful.

```clojure
user=> (require '[honey.sql.pg-ops :refer [=>]])
nil
user=> (sql/format {:select [[[:simple_example]]]})
["SELECT ..."]
user=> (sql/format {:select [[[:complex_example]]]})
["SELECT ..."]
```

Explanation of when to use this feature.
```

### Syntax Validation

**Critical**: All documentation examples must have valid Clojure syntax:
- Properly balanced brackets `[]`, `{}`, `()`
- Complete expressions (don't truncate for brevity)
- Test examples with `clojure -T:build run-doc-tests`

Common mistakes to avoid:
```clojure
;; WRONG - missing closing brackets
(sql/format {:select [[[:make_interval [:=> :secs 10]]]})

;; CORRECT - properly balanced
(sql/format {:select [[[:make_interval [:=> :secs 10]]]]})
```

## Git Workflow

### Commit Message Structure

Follow this pattern for clear commit history:

```
Short descriptive title (imperative mood)

- Bullet points explaining what changed
- Reference issue numbers: "Addresses #123" or "Fixes #456"
- Include any breaking changes or important notes

Part of #123
```

Example:
```
Add PostgreSQL => named parameter operator

- Add operator definition and registration in pg-ops namespace
- Include comprehensive tests for single and multiple parameters
- Add documentation section with real PostgreSQL examples
- All tests pass including documentation validation

Addresses #582
```

### Commit Organization

For feature implementation, use logical commit boundaries:

1. **Core Implementation**: Add the feature
2. **Testing**: Add comprehensive test suite
3. **Documentation**: Add/update documentation
4. **Changelog**: Update CHANGELOG.md

Each commit should be self-contained and leave the codebase in a working state.

## Code Quality Guidelines

### Function Documentation

All public functions should have docstrings:

```clojure
(def =>
  "The => operator for PostgreSQL named parameters in function calls.

  Used in function calls like: [:make_interval [:=> :secs 10]]
  Generates SQL: MAKE_INTERVAL(secs => ?)"
  :=>)
```

### Test Coverage

Aim for comprehensive test coverage:

1. **Basic Usage**: Simple, common cases
2. **Edge Cases**: Multiple parameters, empty values, etc.
3. **Integration**: How it works with other features
4. **Error Cases**: Invalid usage should fail gracefully

Example test structure:
```clojure
(testing "named parameter operator"
  (testing "single parameter"
    (is (= [...] (sql/format [...]))))
  (testing "multiple parameters"
    (is (= [...] (sql/format [...]))))
  (testing "with symbol reference"
    (is (= [...] (sql/format [...]))))
  (testing "real postgresql functions"
    (is (= [...] (sql/format [...])))))
```

### Namespace Organization

- **Core functionality**: `honey.sql`
- **Helper functions**: `honey.sql.helpers`
- **Database-specific**: `honey.sql.pg-ops`, etc.
- **Tests**: Mirror the source structure in `test/`

## Research Guidelines

### PostgreSQL Documentation

When implementing PostgreSQL features:

1. **Check Official Docs**: Always verify syntax in PostgreSQL documentation
2. **Version Compatibility**: Note which PostgreSQL versions support the feature
3. **Legacy vs Modern**: Prefer modern syntax over legacy (e.g., `=>` vs `:=`)

### Example Research Process

For the `=>` operator implementation:
1. Searched PostgreSQL docs for named parameter syntax
2. Found that `=>` is modern standard, `:=` is legacy
3. Verified with real PostgreSQL examples
4. Implemented only the modern `=>` operator

## Performance Considerations

### Testing Performance

- Use appropriate test data sizes
- Don't test with excessive output (causes token limits)
- Use filters like `head`, `tail`, `grep` for large outputs

### Code Efficiency

- Prefer simple, readable implementations
- Follow existing patterns in the codebase
- Use multimethods and protocols where appropriate

## Debugging Guidelines

### Common Issues

1. **Unbalanced Brackets**: Use editor bracket matching
2. **Invalid Symbols**: Check Clojure symbol naming rules
3. **Test Failures**: Run individual test cases to isolate issues
4. **Documentation Errors**: Run `run-doc-tests` frequently

### Debugging Tools

- **REPL**: Primary debugging tool, test every change
- **Error Messages**: Read carefully, often point to exact issue
- **Git Diff**: Review changes before committing
- **Test Output**: Check both success and failure cases

## Documentation Maintenance

### Keep Examples Current

- All code examples must be valid and tested
- Update examples when APIs change
- Include real-world use cases, not just toy examples

### Cross-References

- Link related features in documentation
- Reference GitHub issues where relevant
- Point to official database documentation

## Future Development

### Adding New Features

1. **Research First**: Understand the database feature thoroughly
2. **Design API**: Follow HoneySQL patterns and conventions
3. **Implement Core**: Start with basic functionality
4. **Add Tests**: Comprehensive test coverage
5. **Document**: Clear documentation with examples
6. **Validate**: All tests pass, including documentation tests

### Maintaining Compatibility

- Consider backward compatibility impact
- Document breaking changes clearly
- Follow semantic versioning principles
- Test against multiple Clojure/database versions

This guide captures the essential practices learned during real development work on HoneySQL. Following these guidelines will help maintain code quality and development velocity.
