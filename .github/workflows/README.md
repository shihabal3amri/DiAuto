# CI

Android CI runs tests and builds the github debug and optimized release variants using
JDK 17, SDK 36, NDK 29.0.14206865 and CMake 3.22.1. CI release artifacts are unsigned;
the maintainer signs public APKs privately. No signing secrets are available to PRs.

Publish download site deploys only `site/` to GitHub Pages after regenerating all five
language editions. Pages uses the GitHub Actions deployment source.
