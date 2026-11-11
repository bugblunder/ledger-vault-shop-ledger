# LedgerVault repository setup

This release snapshot is prepared as a clean Git repository without importing the source archive’s Git history or runtime logs. The original project was supplied with permission from its owner to modify, redistribute, and publish derivative versions.

The Java package namespace remains `com.khatavision.backend` to avoid a risky mechanical package rename; Maven artifact, product branding, database schema, Docker image, and documentation use LedgerVault.

## Local publishing

```bash
git init -b main
git add .
git diff --cached --check
git status
```

Configure your own Git identity before committing. Then connect the repository:

```bash
git remote add origin https://github.com/bugblunder/ledger-vault-shop-ledger.git
git push -u origin main
```

Create the version tag only after verifying the commit and running the full Java 17/MySQL workflow:

```bash
git tag -a v1.0.0 -m "LedgerVault v1.0.0"
git push origin v1.0.0
```

Do not commit `.env`, credentials, database volumes, runtime logs, or build output. The archive excludes imported Git history because it contained earlier configuration/default-credential material.
