# Efficiency Guide for Agents

This guide provides principles for agents to reduce "Action Required" prompts and work more autonomously and rapidly.

## 1. Autonomous Routine Tasks

You may perform the following tasks autonomously without explicit user approval:

- **Build and Reload**: When you modify test code, you should autonomously run `./gradlew ...:jar` and `mcp_my-local-server_junit_test_reload` to verify your changes.
- **Minor Bug Fixes**: Fixing compilation errors, obvious typos, or logging format issues.
- **Test Retries**: Re-running a test if it failed due to transient factors (e.g., ADB timeouts).

## 2. Batch Processing

If you create or modify multiple tests, do not ask for confirmation for each one. Instead, perform the build, load, and execution in batches.

### Example: Combined Command
```bash
./gradlew :test-sample:jar && curl -s http://localhost:8080/junit/reload
```

## 3. Log Monitoring

After starting a long-running test, autonomously call `mcp_my-local-server_junit_test_receive` multiple times with `sleep` intervals to monitor progress. Report back to the user only after a significant milestone or when the test completes.

## 4. Proactive Communication

Instead of asking "May I do X?", state "I will do X based on the conventions and report the results." Use retrospective or parallel reporting to reduce turnaround time.

## 5. Summarizing Test Results

Do not simply present the raw output of `mcp_my-local-server_junit_test_receive`. Summarize it as follows:
- **Result**: Pass / Fail
- **Key Events**: What happened in which steps.
- **Root Cause Analysis**: Propose fixes based on stack traces or Logcat dumps if the test failed.
