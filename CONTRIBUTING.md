Contributing
============

If you've found a bug, please contribute a failing test case so we can study and fix it.

Code Contributions
------------------

Get working code on a personal branch with tests passing before you submit a PR:

```
./gradlew test
```

Please make every effort to follow existing conventions and style in order to keep the code as
readable as possible. Our project has `.editorconfig` file, please enable EditorConfig support into your idea.

Contribute code changes through GitHub by forking the repository and sending a pull request. We
squash all pull requests on merge.


Gradle Setup
------------

Use java 11


Releasing
---------

This section is for maintainers. Contributors don't need to do any of this.

Each module is published to Maven Central as its own artifact with its own version. One GitHub
release publishes every module whose version isn't on Maven Central yet.

1. Open a release PR (for example, `chore: release 10.0.0`) that:
   - bumps `version` in the `build.gradle.kts` of each module that changed, plus `GrowthBook`,
     whose version names the release
   - moves the `[Unreleased]` notes in `CHANGELOG.md` under the new version, and updates the
     per-module changelog (`CHANGELOG-<module>.md`) of each other module that changed
2. Merge the release PR.
3. Publish a GitHub release whose tag equals the `GrowthBook` version, such as `10.0.0`, with no
   `v` prefix.

Publishing the release runs `.github/workflows/publish.yml`, which:

- fails unless the tag matches the `GrowthBook` version
- publishes each module in its module list whose version isn't on Maven Central, and skips the rest
- signs the artifacts and uploads them to Sonatype, then transfers them to Maven Central, which
  releases them once validation passes

A new module must be added to the module list in `publish.yml`, or it is never published. Check
[Maven Central](https://central.sonatype.com/namespace/io.growthbook.sdk) after a release; new
versions can take a while to appear.
