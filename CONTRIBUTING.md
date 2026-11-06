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

Each module is published to Maven Central as its own artifact with its own version.
[release-please](https://github.com/googleapis/release-please) manages the versions, changelogs,
tags and GitHub releases, so nothing is bumped by hand.

**Commit messages.** Pull requests are squash-merged, and the PR title becomes the commit that
release-please reads, so titles must follow [Conventional Commits](https://www.conventionalcommits.org)
(CI checks this). `fix:` releases a patch, `feat:` a minor version, and `!` (as in `feat!:`) or a
`BREAKING CHANGE:` footer a major version. Other types, such as `docs:`, `chore:`, `ci:` and `test:`,
don't release anything.

**Which modules release.** Each module directory is a release-please package (see
`release-please-config.json`). A commit releases every module whose directory it changes. Changes
outside the module directories, such as the README or workflows, release nothing.

**Modules, tags and changelogs.** All artifacts use the group `io.growthbook.sdk`.

| Module | Depends on | Tag | Changelog |
| --- | --- | --- | --- |
| `Core` | | `Core-x.y.z` | `CHANGELOG-Core.md` |
| `GrowthBookKotlinxSerialization` | `Core` | `GrowthBookKotlinxSerialization-x.y.z` | `CHANGELOG-GrowthBookKotlinxSerialization.md` |
| `NetworkDispatcherKtor` | `Core` | `NetworkDispatcherKtor-x.y.z` | `CHANGELOG-NetworkDispatcherKtor.md` |
| `NetworkDispatcherOkHttp` | `Core` | `NetworkDispatcherOkHttp-x.y.z` | `CHANGELOG-NetworkDispatcherOkhttp.md` |
| `GrowthBook` | `Core` (api), `GrowthBookKotlinxSerialization` | `x.y.z` | `CHANGELOG.md` |
| `GrowthBookExt` | `GrowthBook` (api) | `GrowthBookExt-x.y.z` | `CHANGELOG-GrowthBookExt.md` |
| `GrowthBookTest` | `GrowthBook` (api) | `GrowthBookTest-x.y.z` | `CHANGELOG-GrowthBookTest.md` |
| `GrowthBookOpenFeature` | `GrowthBook` (api) | `GrowthBookOpenFeature-x.y.z` | `CHANGELOG-GrowthBookOpenFeature.md` |

`GrowthBook` keeps plain tags so they continue the existing tag history; tags from before
release-please are all `GrowthBook` versions. release-please creates a module's changelog file on
its first release if the file doesn't exist yet.

**Dependencies between modules.** Modules depend on each other with `project(":Module")`. When a
module is published, its POM records each dependency at the version in that dependency's
`build.gradle.kts` at that moment. Dependents are not released automatically, so:

- A published dependent keeps pointing at the dependency version it was built with until the
  dependent is released again. If users of a dependent need a dependency's change, release the
  dependent too. For example, a `Core` fix that `GrowthBook` users need also needs a `GrowthBook`
  release.
- Release a dependency together with, or before, its dependents. If a dependent is published
  pointing at a dependency version that isn't on Maven Central, users can't resolve it.
  `publish.yml` publishes every unpublished module in one run, so releasing both in the same
  release PR is enough.
- Choose the dependent's bump from what its own users see. An `api` dependency is part of the
  dependent's public API, so a breaking change in `Core` is also breaking for `GrowthBook`, and a
  breaking change in `GrowthBook` is breaking for `GrowthBookExt`, `GrowthBookTest` and
  `GrowthBookOpenFeature`. An `implementation` dependency is hidden from users, so a dependency's
  breaking change usually needs only a patch or minor release of the dependent.

To release a module that has no changes of its own, merge a commit that changes a file in its
directory, such as a comment in its `build.gradle.kts`, with a `Release-As: x.y.z` footer in the
squash commit message. The footer applies to every module the commit changes, so keep such a
commit to the modules you mean to release. A commit with no changes in the module's directory
doesn't release it, so empty commits and changelog-only edits (the changelogs are at the repo root)
don't work. A `fix:` or `feat:` change of the module's own also releases it.

**Releasing.**

1. After each merge to `main`, release-please opens or updates a release PR that bumps each
   affected module's `version` in its `build.gradle.kts`, `.release-please-manifest.json`, and its
   changelog. Edit the changelog wording in that PR if needed.
2. Merge the release PR when you want to release. release-please tags each released module and
   creates a GitHub release for it. `GrowthBook` keeps plain tags such as `10.1.0`; other modules
   use prefixed tags such as `GrowthBookOpenFeature-1.0.1`.
3. The same workflow then runs `.github/workflows/publish.yml`, which publishes each module in its
   module list whose version isn't on Maven Central yet. It signs the artifacts, uploads them to
   Sonatype and transfers them to Maven Central, which releases them once validation passes.

If publishing fails, fix the cause and run **Publish to Maven Central** manually from the Actions
tab; it skips anything already published.

**Adding a module.** A new module needs:

1. An entry in `release-please-config.json` with its `component`, `changelog-path` and the
   `build.gradle.kts` extra file, copied from an existing module.
2. Its version in `.release-please-manifest.json`, matching the `version` in its
   `build.gradle.kts`.
3. The `// x-release-please-version` marker at the end of its `version` line.
4. Its name in the module list in `.github/workflows/publish.yml`, and in the table above.
5. A complete `pom {}` block: name, description, url, licenses, scm and developers. Maven Central
   rejects the whole upload, including other modules, if any of these is missing.

`publish.yml` publishes every listed module whose version isn't on Maven Central, so a new module
is published by the next publish run, even one triggered by another module's release. Set its
version to the first version you want published, and don't add it to the `publish.yml` list until
it's ready to release.

**Checking a release.** Check [Maven Central](https://central.sonatype.com/namespace/io.growthbook.sdk)
after a release; new versions can take a while to appear. If a publish run fails validation,
nothing in that run is published, so retrying republishes every module from it.

### Publishing secrets

`publish.yml` reads four GitHub Actions secrets (**Settings → Secrets and variables → Actions**).
Never commit their values; keep the originals in the team password manager, because GitHub can't
show a secret again after it's saved.

| Secret | Value |
| --- | --- |
| `GB_SONATYPE_USERNAME` | Username of a Sonatype Central user token |
| `GB_SONATYPE_PASSWORD` | Password of the same user token |
| `GPG_PRIVATE_KEY` | ASCII-armored private signing key |
| `GPG_PRIVATE_PASSWORD` | Passphrase of the signing key |

**Sonatype user token.** Sign in to [central.sonatype.com](https://central.sonatype.com) with an
account that can publish to the `io.growthbook.sdk` namespace, open **View Account**, and choose
**Generate User Token**. Use the token's username and password, not the account login.

**Signing key.** Maven Central rejects artifacts unless their signing key's public half is on a
supported keyserver. To create a key:

```sh
gpg --quick-generate-key "GrowthBook SDK <sdk-dev@growthbook.io>" rsa4096 sign 2y
gpg --list-secret-keys --keyid-format long sdk-dev@growthbook.io   # note the key ID

# Publish the public key; keys.openpgp.org emails sdk-dev@growthbook.io to confirm the identity
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --export <KEY_ID> | curl -T - https://keys.openpgp.org

# Value for GPG_PRIVATE_KEY
gpg --armor --export-secret-keys <KEY_ID>
```

**Rotating.** Before a key expires, extend it with `gpg --quick-set-expire <FINGERPRINT> 2y`
(and `gpg --quick-set-expire <FINGERPRINT> 2y '*'` for its subkeys), publish the public key again,
and update `GPG_PRIVATE_KEY`. A new key works too: publish it and update both GPG secrets.
Artifacts released earlier keep their original signatures either way.
