# Release signing

The key previously stored at `app/release.keystore` and the passwords committed in the Gradle file must be treated as compromised. Do not reuse them. The repository change removes the tracked key and hard-coded signing configuration. Debug builds use Android's generated debug key; release signing is intentionally disabled until a replacement signing plan is completed.

## Before the next release

1. Decide how existing installations will receive updates. A new Android signing key will not update APKs signed with the old key unless the distribution channel supports a signing-key upgrade. For Google Play, verify Play App Signing and its key-upgrade process first.
2. Generate a new keystore in a private location outside this repository and keep an offline backup.
3. Store the new signing data in GitHub Actions repository secrets under Settings → Secrets and variables → Actions: `CARDCONTROL_KEYSTORE_BASE64`, `CARDCONTROL_KEYSTORE_PASSWORD`, `CARDCONTROL_KEY_ALIAS`, and `CARDCONTROL_KEY_PASSWORD`.
4. Update the Gradle release configuration to consume the replacement keystore and credentials from environment variables or ignored local configuration. Do not commit the keystore, passwords, or local signing properties.
5. Test the signed APK's certificate and checksum, then test the update path on a device before publishing.

The tag-release workflow currently blocks publication while the replacement Gradle signing configuration and secrets are incomplete. Keep releases blocked until these steps are finished; the workflow creates draft releases for human review.

Removing the keystore from the current tree does not remove copies from prior commits, forks, clones, or caches. Rotation is still required. Rewriting public history to purge the old binary and passwords changes commit IDs and cannot recall copies already made.

## Repository settings still required

- Under Settings → Rules → Rulesets, create an active ruleset targeting `main`. Require pull requests and the `Unit tests`, `Android Lint`, and `Analyze Kotlin and Java` checks before merging. Block force pushes and branch deletion.
- Enable the Dependency Graph under Settings → Code security and analysis, then set repository variable `DEPENDENCY_GRAPH_ENABLED=true` so the current Dependency Review and Dependency Submission workflows run instead of being skipped.
