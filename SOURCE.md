# Source provenance and authorization

LedgerVault is based on the source snapshot supplied for this review, whose embedded Git configuration referenced:

https://github.com/adityaprakash10/ledger_app

The project owner has expressly authorized the current maintainer to modify the source, redistribute it publicly, and publish derivative versions under the maintainer’s GitHub account. This release therefore uses LedgerVault branding and a new repository history.

The supplied snapshot already contained substantial changes to authorization, identity handling, transactional ledger operations, append-only corrections, archival, protective database migrations, regression and MySQL integration tests, demo tooling, documentation, and CI configuration. The LedgerVault preparation pass updates product branding, Maven artifact metadata, database naming, Docker image/JAR naming, and publishing documentation.

The original Git history is not imported because it contained earlier configuration/default-credential material. The Java package namespace `com.khatavision.backend` is retained for compatibility and is not a claim of affiliation with the original repository owner. No new open-source licence is asserted by this snapshot; add one only if the rights holder has explicitly authorized the selected licence.
