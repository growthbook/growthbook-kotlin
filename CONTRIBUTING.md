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

Each module is published to Maven Central (group `io.growthbook.sdk`) with its own version.
[release-please](https://github.com/googleapis/release-please) manages versions, changelogs, tags
and GitHub releases; nothing is bumped by hand.

**What gets released.** PRs are squash-merged, so the PR title is the commit release-please reads
and must follow [Conventional Commits](https://www.conventionalcommits.org) (CI checks this).
`fix:` releases a patch, `feat:` a minor and `feat!:` or a `BREAKING CHANGE:` footer a major
version, for each module whose directory the commit changes. Other types, and changes outside
module directories, release nothing.

**Tags and changelogs.** `GrowthBook` uses plain tags (`10.1.0`), continuing the repo's earlier tags,
and `CHANGELOG.md`. Other modules use `<Module>-x.y.z` tags and `CHANGELOG-<Module>.md` (see
`release-please-config.json`).

**Releasing.**

1. After each merge to `main`, release-please opens or updates a release PR that bumps the affected
   modules' versions and changelogs. Edit the changelog wording there if needed.
2. Merging the release PR tags and creates a GitHub release for each released module, then runs
   `publish.yml`, which publishes every module in its list whose version isn't on Maven Central yet.
3. Check [Maven Central](https://central.sonatype.com/namespace/io.growthbook.sdk); new versions can
   take about 30 minutes to appear. If the publish fails, nothing in that run is published: fix the
   cause and run **Publish to Maven Central** from the Actions tab.

**Dependencies between modules.** `Core` is used by `GrowthBook`, `GrowthBookKotlinxSerialization`
and both `NetworkDispatcher*` modules; `GrowthBook` is used by `GrowthBookExt`, `GrowthBookTest` and
`GrowthBookOpenFeature`. Releasing a module doesn't release its dependents, and a published module
keeps the dependency versions it was built with. So:

- If a dependent's users need a dependency's change (for example a `Core` fix for `GrowthBook`
  users), release the dependent in the same release PR.
- `Core` is an `api` dependency of `GrowthBook`, and `GrowthBook` of its three dependents, so a
  breaking change in either is also a breaking change for those dependents.
- To release a module with no changes of its own, merge a commit that changes a file in its
  directory with a `Release-As: x.y.z` footer. The footer applies to every module the commit
  changes, and changelog-only or empty commits release nothing.

**Adding a module.** Add it to `release-please-config.json` and `.release-please-manifest.json`
(matching its `build.gradle.kts` version), put `// x-release-please-version` on its `version` line,
and give it a complete `pom {}` block, including `developers`; Maven Central rejects the whole
upload if one is missing. Add it to the `publish.yml` list only when it's ready, since the next
publish run publishes any listed module that isn't on Maven Central yet.

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
