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

A module isn't released just because a module it depends on was: if a `Core` fix should reach
`GrowthBook` users, `GrowthBook` needs a release too. To force one, merge a commit that touches the
module with a `Release-As: x.y.z` footer, or bump it with a `fix:` or `feat:` change of its own.

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

A new module must be added to both `release-please-config.json` and the module list in
`publish.yml`, and its `version` line needs the `// x-release-please-version` marker. Check
[Maven Central](https://central.sonatype.com/namespace/io.growthbook.sdk) after a release; new
versions can take a while to appear.

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
