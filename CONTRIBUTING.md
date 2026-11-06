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
