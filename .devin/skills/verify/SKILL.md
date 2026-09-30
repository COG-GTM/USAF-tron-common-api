---
name: verify
description: Independently run the tests that cover the current diff and report pass or fail with the failing assertions.
agent: test-runner
---

Map each changed file under `src/main/java` to its test class under `src/test/java`. Run `./mvnw -q test -Dtest='<Class1>,<Class2>'`. Report the command, pass/fail counts, and the first failing assertion for each failure. Do not edit any file.
