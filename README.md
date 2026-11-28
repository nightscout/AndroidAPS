# AAPS translation vocabulary

This branch holds the shared vocabulary for the AAPS translations on Crowdin: the app (Crowdin
project AndroidAPS) and the wiki (project AndroidAPSdocs). Like the `versions` branch, it holds data
only and has no app code.

The goal: every translator and every translation session uses the same words, also when the work is
done on another computer or with another tool.

## Files

`glossary-<lang>.md`: one file per language. `<lang>` is the Crowdin language code, for example
`cs`, `nl`, `pt-BR` or `es-ES`.

A glossary has:

- style rules at the top: how to address the reader, numbers, units, quotes, what stays in English;
- terms as `English = translation`. A note in brackets says where a word comes from: `(app)` means
  the app uses this word, `(app <key>)` also names the app string;
- decisions: which word was chosen where two were possible, why, and when.

The rules in a language file win over this README.

## Rules

- Where the app has a word, use the same word in the wiki, so the text matches the screen. If the
  app word is wrong, fix the app string on Crowdin too.
- One English term has one translation in a language. To change a term, change it here first.
- Write down why a word was chosen, so the next translator does not change it back.
- This branch is public: no personal data, tokens, passwords or local paths.
- Some notes name work files of the translation scripts (`<WORK>/titles.txt`, `<WORK>/app_all.json`).
  Ignore them if you do not use those scripts.

## Read

One file, no account needed:

```
https://raw.githubusercontent.com/nightscout/AndroidAPS/refs/heads/vocabulary/glossary-nl.md
```

All files, without the history of the app (less than 1 MB):

```
git clone --single-branch --branch vocabulary https://github.com/nightscout/AndroidAPS.git aaps-vocabulary
```

Without `--single-branch`, git downloads the whole AAPS repository.

Run `git pull` before every translation session: another place may have changed terms.

## Change

Commit and push soon after you add or change terms, so other sessions see them. One commit per
language and round is enough, for example `nl: terms from the Omnipod pages`. Commit only the files
you changed.

- With write access to nightscout/AndroidAPS: push to `vocabulary`.
- Without: fork the repository and open a pull request against the `vocabulary` branch.

If two places changed the same line in different ways, keep both and ask the proofreader of that
language which one is right.
