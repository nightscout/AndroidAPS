This document specify hints and good practices for source code contributions.

AndroidAPS is community effort and all contributions are welcome! If you wish help us improving AAPS - please read and try to adhere to 
this guidelines, to make the development and process of change approval as smooth as possible :) 

General rules
=============

* There are plenty of ways you can help, some of them are listed on wiki:
  https://wiki.aaps.app/en/latest/SupportingAaps/HowCanIHelp.html
* If you wish to help with documentation or translating:
  https://wiki.aaps.app/en/latest/SupportingAaps/Translations.html
  
Development guidelines
======================

Coding conventions
-----------------
1. Use Android Studio with default indents (4 chars, use spaces)
2. Use autoformat feature CTRL-ALT-L in every changed file before commit

Committing Changes / Pull Requests
---------------------------------

1. Make a fork of [repository](https://github.com/nightscout/AndroidAPS) on GitHub (be aware to uncheck the box "Copy the master branch only")
2. Create separate branch for each feature, branch from most recent dev
3. Commit all changes to your fork
4. When ready, rebase on top of dev and make Pull Request to main repo

Naming Conventions for Pull Requests / Branches
-----------------------------------------------

TODO

Translations
------------

* If possible, always use Android translation mechanism (with strings.xml and @strings/id) instead of hardcoded texts
* Provide only English strings - all other languages will be crowd translated via Crowdin (https://crowdin.com/project/androidaps and https://crowdin.com/project/androidapsdocs)

Hints
-----

* Start small, it is easier to review smaller changes that affect fewer parts of code 
* Take a look into Issues list (https://github.com/nightscout/AndroidAPS/issues) - maybe there is something you can fix or implement
* For new features, make sure there is Issue to track progress and have on-topic discussion
* Reach out to community, discuss idea on Discord (https://discord.gg/4fQUWHZ4Mw)
* Speak with other developers to minimize merge conflicts. Find out who worked, working or plan to work on specific issue or part of app
