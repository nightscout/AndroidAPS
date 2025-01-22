# Using GitHub Actions for browser build

These instructions allow you to build AndroidAPS with a browser.


## Setup Github AndroidAPS repository

1. Fork https://github.com/nightscout/AndroidAPS into your account. If you already have a fork of AndroidAPS in GitHub, you can't make another one. You can continue to work with your existing fork, or delete that from GitHub and then fork https://github.com/nightscout/AndroidAPS.


## Prepare your existing or new Android Studio signing keystore

1. Base64 encoding of the keystore file (on a Windows PC):\
   certutil -encode keystore.jks SIGNING_KEY.txt
2. Base64 encoding of the keystore file (on other plattforms with openssl):\
   openssl base64 < keystore.jks | tr -d '\n' | tee SIGNING_KEY.txt
3. Base64 encoding of the keystore file (on macOS):\
   base64 keystore.jks > SIGNING_KEY.txt


## Setup Github secrets for the keystore and the Dropbox APP

1. In the forked AndroidAPS repository, go to Settings -> Secrets and variables -> Actions.
1. For each of the following secrets, tap on "New repository secret", then add the name of the secret, along with the value you defined during keystore creation time. As value for the secret SIGNING_KEY use the text out of the file SIGNING_KEY.txt, which is stored between the two lines --BEGIN CERTIFICATE-- and --END CERTIFICATE--.  
    * `KEY_STORE_PASSWORD`
    * `KEY_ALIAS`
    * `KEY_PASSWORD`
    * `SIGNING_KEY`
1. For the Dropbox upload of the build AndroidAPS app, the following secrets have to be defined:
    * `DROPBOX_APP_KEY`
    * `DROPBOX_APP_SECRET`
    * `DROPBOX_REFRESH_TOKEN`
   See manual of dropbox-github-action on\
   https://github.com/marketplace/actions/dropbox-github-action \
   to define these three Github secrets.\
   Note:\
   On Windows 10 or Windows 11 systems, the curl command is available.\
   But the command mentioned in the manual of dropbox-github-action has to be inserterted as one line, without the "\\"
   sign. 


## Build AndroidAPS
1. On your forked AndroidAPS repository, go to Actions.
2. If not already done, activate workflow on your repository.
3. On the left side, select the workflow "Build app version to Dropbox".
4. On the right side, click on the drop down menue "Run workflow" and select "Branch: master" which is the default value.
5. Then click on "Run workflow".


## Upload the build and signed app to Dropbox (direct through the workflow)
1. When the workflow (build, sign, upload to Dropbox) is completed,
   look into your Dropbox App and see the build and signed AAPS apk file.
