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


## Setup Github secrets for the keystore

1. In the forked AndroidAPS repository, go to Settings -> Secrets and variables -> Actions.
1. For each of the following secrets, tap on "New repository secret", then add the name of the secret, along with the value you defined during keystore creation time. As value for the secret SIGNING_KEY use the text out of the file SIGNING_KEY.txt, which is stored between the two lines --BEGIN CERTIFICATE-- and --END CERTIFICATE--.  
    * `KEY_STORE_PASSWORD`
    * `KEY_ALIAS`
    * `KEY_PASSWORD`
    * `SIGNING_KEY`


### Set up Google API access and download the JSON file
1. Go to the [Gooogle Cloud Console](https://console.cloud.google.com)
2. Create a Google Cloud project (Project Selection Menu/New Project) with name "Google Drive API") and click Create.
3. Enable the Google Drive API [API & Services -> Library](https://console.cloud.google.com/apis/library), search for "Google Drive API" and click Enable.
4. Create Credentials for OAuth 2.0 Client IDs. Go to [API & Services -> Credentials](https://console.cloud.google.com/apis/credentials), click "Create Credentials" -> "OAuth Client ID".\
   If prompted, configure the OAuth Consent Screen
   * Choose "External" (for personal use)
   * Enter an App Name
   * Click "Add user" and add your email address
   * If possible, chose "Publishing status" to "In production",\
     otherwise the later defined refresh token is only valid for 7 days.
   * Click "Save & Continue" until you reach the last page
   * Click "Back to Dashboard"
5. Now create your OAuth credentials:
   * select "Web Application" as application type
   * Click Create
   * Define the "Redirect URI" as "http://localhost"
   * Click "Download JSON" (this will be your client_secret.json file)
   * Save it securely, as it is required for API access
   * Note the value for GOOGLE_CLIENT_ID (value of client_id in the client_secret-json file)
   * Note the value for GOOGLE_CLIENT_SECRET (value of client_secret in the client_secret-json file)

## Generate the refresh token

Visit the following URI in your browser to get the authorization code.

```
https://accounts.google.com/o/oauth2/auth?client_id=CLIENT_ID&response_type=code&scope=https://www.googleapis.com/auth/drive&redirect_uri=http://localhost&access_type=offline
```
The response is like:\
http://localhost/?code={AUTHORIZATION_CODE}&scope=https://www.googleapis.com/auth/drive

Then, we can get the refresh token by the following command. Please use the authorization code you have just got in the previous step in the browser.
Take care, the authorisation code gets invalid after a short time. So you have to renew it in this case.
```
$ curl -X POST https://oauth2.googleapis.com/token \
    -d client_id=YOUR_CLIENT_ID \
    -d client_secret=YOUR_CLIENT_SECRET \
    -d code=AUTHORIZATION_CODE \
    -d grant_type=authorization_code \
    -d redirect_uri=http://localhost
```
   Note:\
   On Windows 10 or Windows 11 systems, the curl command is available.\
   But the command above has to be inserterted as one line, without the "\\"
   sign. 
   
The response will be like the following.

```
{
  "access_token": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "expires_in": 3599,
  "refresh_token": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "scope": "https://www.googleapis.com/auth/drive",
  "token_type": "Bearer"
}
```

Keep the `refresh_token` for later use.
   
## Prepare your new Google API key

1. Base64 encoding of the Google API JSON key file (on a Windows PC):\
   certutil -encode client_secret.json GOOGLE_CLIENT_SECRET_JSON.txt
2. Base64 encoding of the Google API JSON key file (on other plattforms with openssl):\
   openssl base64 < client_secret.json | tr -d '\n' | tee GOOGLE_CLIENT_SECRET_JSON.txt
3. Base64 encoding of the Google API JSON key file (on macOS):\
   base64 client_secret.json > GOOGLE_CLIENT_SECRET_JSON.txt

## Setup Github secrets for the Google Drive API
1. For the Google Drive upload of the build AndroidAPS app, the following secrets have to be defined:
    * `GOOGLE_DRIVE_FOLDER_ID`
    * `GOOGLE_CLIENT_ID`
    * `GOOGLE_CLIENT_SECRET`
    * `GOOGLE_CLIENT_SECRET_JSON`
    * `GOOGLE_REFRESH_TOKEN`

1. In the forked AndroidAPS repository, go to Settings -> Secrets and variables -> Actions.
1. Add the new repository secret GOOGLE_CLIENT_SECRET_JSON.\
   As value for the secret GOOGLE_CLIENT_SECRET_JSON use the text out of the file GOOGLE_CLIENT_SECRET_JSON.txt,\
   which is stored between the two lines --BEGIN CERTIFICATE-- and --END CERTIFICATE--.  
2. Add the new repository secret GOOGLE_CLIENT_ID and enter the value you note above.
3. Add the new repository secret GOOGLE_REFRESH_TOKEN and enter the value you note above.

### Setup Github secret GOOGLE_DRIVE_FOLDER_ID for the upload folder on Google Drive

Go to your **Google Drive** and find the folder you want your files to be uploaded to and take note of **the folder's ID**, the long set of characters after the last `/` in your browsers address bar. Store it as new Github secret named GOOGLE_DRIVE_FOLDER_ID. If you only want to store it in the root folder of your Google Drives filesystem, then enter the value "root" (without quotation marks) as value for the secret GOOGLE_DRIVE_FOLDER_ID.

## Build AndroidAPS
1. On your forked AndroidAPS repository, go to Actions.
2. If not already done, activate workflow on your repository.
3. On the left side, select the workflow "Build app version to Google Drive".
4. On the right side, click on the drop down menue "Run workflow" and select "Branch: master" which is the default value.
5. Then click on "Run workflow".


## Upload the build and signed app to Google Drive (direct through the workflow)
1. When the workflow (build, sign, upload to Google Drive) is completed,
   look into your Google Drive App and see the build and signed AAPS apk file.
