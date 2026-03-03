# Skill: Test-Driven Development (TDD) Expert

## Context
Use this skill when you need to design and implement tests using TDD principles, or when creating unit tests for new features and refactoring. This skill guides you through the Red-Green-Refactor cycle and ensures high-quality, well-tested code.

## When to Use This Skill
- **Trigger 1:** User asks to "write tests" or "create unit tests"
- **Trigger 2:** Implementing a new feature (write tests first)
- **Trigger 3:** Refactoring legacy code (add tests to capture behavior first)
- **Trigger 4:** User mentions TDD, JUnit5, Mockito, testing strategy
- **Prerequisite:** Basic understanding of Java and the project's structure

## Core Principles & Guidelines

### 1. Red-Green-Refactor Cycle
- **Red Phase:** Write a failing test that specifies desired behavior
- **Green Phase:** Write minimal code to make the test pass
- **Refactor Phase:** Improve code while keeping tests green

### 2. Test Structure (Arrange-Act-Assert)
```java
// Arrange: Setup test data and mocks
OrderRepository mockRepo = Mockito.mock(OrderRepository.class);
Order testOrder = new Order("CUST123", 100.0);

// Act: Execute the code under test
Order result = orderService.createOrder("CUST123", 100.0);

// Assert: Verify the expected outcome
assertEquals("CUST123", result.getCustomerId());
verify(mockRepo).save(any(Order.class));
```

### 3. Test Naming & Clarity
- `@DisplayName` is mandatory on every JUnit test method.
- `@DisplayName` must describe behavior and condition (e.g., "should map one event when parameters are absent").
- Keep method names descriptive, but treat `@DisplayName` as the canonical readable contract.
- Each test should test ONE behavior

### 4. Meaningful Assertions
- Assert behavior, not implementation details
- Use fluent assertions: `assertThat(result).isNotNull().hasSize(5)`
- Avoid trivial assertions on getters/setters unless part of contract

### 5. Test Isolation & Independence
- Each test must be independent and run in any order
- Use mocks to isolate the unit under test from dependencies
- Use `@BeforeEach` for setup, `@AfterEach` for cleanup
- Avoid shared state between tests

## Step-by-Step Workflow

1. **Understand Requirements:** What should the code do? Write it as a test first.
2. **Write Failing Test:** Create `@Test` method that specifies desired behavior.
3. **Make Test Pass:** Write minimal implementation (don't optimize yet).
4. **Refactor:** Improve code while re-running tests to ensure they still pass.
5. **Add Edge Cases:** Use `@ParameterizedTest` for boundary conditions.
6. **Verify Coverage:** Ensure ≥80% code coverage for business logic.

## Test Framework Proficiency

### JUnit5 (Jupiter)
- `@Test` — Mark method as a test
- `@DisplayName("description")` — Mandatory human-readable test behavior
- `@BeforeEach` / `@AfterEach` — Setup and teardown per test
- `@ParameterizedTest` with `@ValueSource`, `@CsvSource`, `@MethodSource` — Mandatory when validating multiple scenarios of the same behavior
- `assertThrows()` — Verify exception handling

### Mockito
- `Mockito.mock(Class.class)` — Create mock object
- `when(mock.method()).thenReturn(value)` — Stub method behavior
- `verify(mock).method()` — Assert method was called
- `spy(realObject)` — Wrap real object, selectively mock methods
- `any()` — Matcher for "any argument"

### AssertJ / Hamcrest
- Fluent assertions: `assertThat(result).isNotNull().hasSize(5)`
- Custom matchers for domain objects
- Readable error messages on assertion failures

## Test Coverage Standards

- **Unit Tests:** ≥80% code coverage for business logic
- **Critical Paths:** 100% coverage for security, validation, error handling
- **Isolated Tests:** Each test <100ms, independent, deterministic
- **Meaningful Tests:** Tests document behavior; tests are executable specifications

## Anti-Patterns to Avoid

- ❌ **Testing Implementation Details:** Test behavior, not how it's implemented
- ❌ **Overly Mocked Tests:** Mock dependencies, not the class under test
- ❌ **Shared State:** Tests should not depend on execution order
- ❌ **Slow Tests:** Tests >1s indicate tight coupling or real I/O
- ❌ **Poor Test Names:** `test1()`, `testMethod()` provide no context
- ❌ **Ignoring Failures:** Don't skip tests without clear explanation
- ❌ **Testing Getters/Setters:** Unless they're part of a contract or contain logic
- ❌ **Missing DisplayName:** Tests without `@DisplayName` reduce readability in reports
- ❌ **Scenario Duplication:** Multiple near-identical tests instead of one `@ParameterizedTest`

## Quality Bar & Verification

A test suite is complete when:
- [ ] All tests pass locally and in CI
- [ ] ≥80% code coverage for business logic
- [ ] Tests document expected behavior (readable as specification)
- [ ] Each test is independent and runs in <100ms
- [ ] No flaky tests (tests that intermittently fail)
- [ ] Edge cases and error conditions are tested
- [ ] Mocks are used appropriately (not overused)
- [ ] Every test method has `@DisplayName`
- [ ] Repeated scenario checks are consolidated into `@ParameterizedTest`

## Example Application

### TDD Flow for OrderService

```java
// 1. RED: Write failing test
@Test
@DisplayName("should create order when payment succeeds")
void testCreateOrderSuccess() {
    // Arrange
    OrderRepository mockRepo = Mockito.mock(OrderRepository.class);
    PaymentGateway mockPayment = Mockito.mock(PaymentGateway.class);
    when(mockPayment.charge(100.0)).thenReturn(true);
    
    OrderService service = new OrderService(mockRepo, mockPayment);
    
    // Act
    Order result = service.createOrder("CUST123", 100.0);
    
    // Assert
    assertThat(result).isNotNull();
    assertEquals("CUST123", result.getCustomerId());
    verify(mockPayment).charge(100.0);
    verify(mockRepo).save(any(Order.class));
}

// 2. GREEN: Minimal implementation
class OrderService {
    OrderService(OrderRepository repo, PaymentGateway payment) { ... }
    
    Order createOrder(String customerId, double amount) {
        payment.charge(amount);
        Order order = new Order(customerId, amount);
        repo.save(order);
        return order;
    }
}

// 3. REFACTOR: Improve design while tests remain green
// Add validation, error handling, logging, etc.

// 4. ADD EDGE CASES
@ParameterizedTest
@ValueSource(doubles = { -10.0, 0.0 })
@DisplayName("should reject invalid amounts")
void testInvalidAmounts(double amount) {
    assertThrows(IllegalArgumentException.class, 
        () -> service.createOrder("CUST123", amount));
}
```

## Tips for TDD Success

1. **Test First, Always:** The test clarifies what you're building before you build it.
2. **One Test at a Time:** Write one failing test, make it pass, then write the next.
3. **Keep Tests Simple:** If a test is hard to write, the code design is probably wrong.
4. **Refactor Tests Too:** Tests are code; keep them clean and maintainable.
5. **Use Parameterized Tests:** Avoid test duplication with `@ParameterizedTest`.
