This document speciffy hints and good practices for source code contributions.

AndroidAPS is community effort and all contributions are welcome! If you wish help us improving AndroidAPS - please read and try to adhere to 
this guidelines, to make the development and process of change aproval as smooth as possible :) 

General rules
=============

* There are plenty of ways you can help, some of them are listed on wiki: 
  https://androidaps.readthedocs.io/en/latest/EN/Getting-Started/How-can-I-help.html
* If you wish to help with documentation or translating: 
  https://androidaps.readthedocs.io/en/latest/EN/translations.html
  
Development guidelines
======================

Coding convetions
-----------------
1. Use Android Studio with default indents (4 chars, use spaces)
2. Use autoformat feature CTRL-ALT-L in every changed file before commit

Commiting Changes / Pull Requests
---------------------------------

1. Make fork of repository on github
2. Create separate branch for each feature, branch from most recent dev
3. Commit all changes to your fork
4. When ready, rebase on top of dev and make pull request to main repo

Naming Conventions for Pull Requests / Branches
-----------------------------------------------

TODO

Translations
------------

* If possible, always use Android translation mechanism (with strings.xml and @strings/id) instead of hardcoded texts
* Provide only English strings - all other languages will be crowd translated via Crowdn https://translations.androidaps.org/

Hints
-----

* Start small, it is easier to review smaller changes that affect fewer parts of code 
* Take a look into Issues list (https://github.com/MilosKozak/AndroidAPS/issues) - maybe there is somthing you can fix or implement
* For new features, make sure there is Issue to track progress and have on-topic discussion
* Reach out to community, discuss idea on Gitter (https://gitter.im/MilosKozak/AndroidAPS)
* Speak with other developers to minimise merge conflicts. Find out who worked, working or plan to work on speciffic issue or part of app
