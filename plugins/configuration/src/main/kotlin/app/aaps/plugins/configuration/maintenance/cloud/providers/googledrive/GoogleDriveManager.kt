package app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive

import android.content.Context
import android.net.Uri
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.events.EventCloudStorageStatusChanged
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveManager @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val context: Context
) {
    
    companion object {
        private const val CLIENT_ID = "705061051276-3ied5cqa3kqhb0hpr7p0rggoffhq46ef.apps.googleusercontent.com"
        private const val REDIRECT_PORT = 8080
        private const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/oauth/callback"
        private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3"
        private val LOG_PREFIX = CloudConstants.LOG_PREFIX
        
        // SharedPreferences keys
        private const val PREF_GOOGLE_DRIVE_REFRESH_TOKEN = "google_drive_refresh_token"
        private const val PREF_GOOGLE_DRIVE_ACCESS_TOKEN = "google_drive_access_token"
        private const val PREF_GOOGLE_DRIVE_TOKEN_EXPIRY = "google_drive_token_expiry"
        private const val PREF_GOOGLE_DRIVE_STORAGE_TYPE = "google_drive_storage_type"
        private const val PREF_GOOGLE_DRIVE_FOLDER_ID = "google_drive_folder_id"
        
        // Storage types
        const val STORAGE_TYPE_LOCAL = "local"
        const val STORAGE_TYPE_GOOGLE_DRIVE = "google_drive"
        
        // Notification IDs
        const val NOTIFICATION_GOOGLE_DRIVE_ERROR = Notification.USER_MESSAGE + 100

    }
    
    private val client = OkHttpClient()
    private val pathCache = mutableMapOf<String, String>() // cache for resolved folder paths

    // Error state tracking
    private var connectionError = false
    private var errorNotificationId: Int? = null
    
    // Local server related
    private var localServer: ServerSocket? = null
    private var authCodeReceived: String? = null
    private var authState: String? = null
    private var serverJob: Job? = null
    
    /**
     * Check if there is a valid refresh token
     */
    fun hasValidRefreshToken(): Boolean {
        return sp.getString(PREF_GOOGLE_DRIVE_REFRESH_TOKEN, "").isNotBlank()
    }
    
    /**
     * Get current storage type with default value
     */
    fun getStorageType(): String {
        val storageType = sp.getString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, STORAGE_TYPE_LOCAL)
        // If there is a refresh token but storage type is local, settings may have been reset, try to restore
        if (storageType == STORAGE_TYPE_LOCAL && hasValidRefreshToken()) {
            // Check if there is a valid folder ID
            val folderId = sp.getString(PREF_GOOGLE_DRIVE_FOLDER_ID, "")
            if (folderId.isNotEmpty()) {
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX Restoring Google Drive storage type from token presence")
                sp.putString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, STORAGE_TYPE_GOOGLE_DRIVE)
                return STORAGE_TYPE_GOOGLE_DRIVE
            }
        }
        return storageType
    }
    
    /**
     * Set storage type
     */
    fun setStorageType(type: String) {
        sp.putString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, type)
    }
    
    /**
     * Start OAuth2 authentication flow using PKCE
     * 
     * Note: This implementation uses a local server approach instead of traditional Android OAuth2
     * because AAPS is an open-source medical application where each user must compile their own APK.
     * Since each compilation uses a different JKS (Java KeyStore) for signing, we cannot rely on
     * a fixed JKS for Google OAuth2 verification. Therefore, we use a local HTTP server to receive
     * the OAuth callback, which works regardless of the APK signing certificate.
     */
    suspend fun startPKCEAuth(): String {
        return withContext(Dispatchers.IO) {
            try {
                // Start local server
                startLocalServer()
                
                // Generate code verifier and code challenge
                val codeVerifier = generateCodeVerifier()
                val codeChallenge = generateCodeChallenge(codeVerifier)
                
                // Save code verifier for later use
                sp.putString("google_drive_code_verifier", codeVerifier)
                
                // Build authorization URL
                val authUrl = buildAuthUrl(codeChallenge)
                aapsLogger.debug(LTag.CORE, "$LOG_PREFIX Google Drive auth URL: $authUrl")
                
                authUrl
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error starting PKCE auth", e)
                throw e
            }
        }
    }
    
    /**
     * Handle authorization code and obtain refresh token
     */
    suspend fun exchangeCodeForTokens(authCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val codeVerifier = sp.getString("google_drive_code_verifier", "")
                if (codeVerifier.isEmpty()) {
                    throw IllegalStateException("Code verifier not found")
                }
                
                val requestBody = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("code", authCode)
                    .add("code_verifier", codeVerifier)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", REDIRECT_URI)
                    .build()
                
                val request = Request.Builder()
                    .url(TOKEN_URL)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val refreshToken = jsonResponse.optString("refresh_token")
                    val accessToken = jsonResponse.optString("access_token")
                    val expiresIn = jsonResponse.optLong("expires_in", 3600)
                    
                    if (refreshToken.isNotEmpty()) {
                        sp.putString(PREF_GOOGLE_DRIVE_REFRESH_TOKEN, refreshToken)
                        sp.putString(PREF_GOOGLE_DRIVE_ACCESS_TOKEN, accessToken)
                        sp.putLong(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY, System.currentTimeMillis() + expiresIn * 1000)
                        
                        // Clear code verifier
                        sp.remove("google_drive_code_verifier")
                        
                        // Clear any previous connection error since authorization succeeded
                        clearConnectionError()
                        
                        aapsLogger.info(LTag.CORE, "$LOG_PREFIX Google Drive tokens obtained successfully")
                        return@withContext true
                    }
                }
                
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Failed to exchange code for tokens: $responseBody")
                false
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error exchanging code for tokens", e)
                false
            }
        }
    }
    
    /**
     * Get a valid access token
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val cachedToken = sp.getString(PREF_GOOGLE_DRIVE_ACCESS_TOKEN, "")
            val expiry = sp.getLong(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY, 0)

            // If token still has more than 5 minutes of validity, use directly
            if (cachedToken.isNotEmpty() && System.currentTimeMillis() < expiry - 300_000) {
                return@withContext cachedToken
            }

            val refreshToken = sp.getString(PREF_GOOGLE_DRIVE_REFRESH_TOKEN, "")
            if (refreshToken.isEmpty()) {
                aapsLogger.warn(LTag.CORE, "$LOG_PREFIX Missing refresh token when refreshing access token")
                showConnectionError(rh.gs(R.string.cloud_token_expired_or_invalid))
                return@withContext null
            }

            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val newAccessToken = jsonResponse.optString("access_token")
                val expiresIn = jsonResponse.optLong("expires_in", 3600)

                if (newAccessToken.isNotEmpty()) {
                    sp.putString(PREF_GOOGLE_DRIVE_ACCESS_TOKEN, newAccessToken)
                    sp.putLong(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY, System.currentTimeMillis() + expiresIn * 1000)
                    clearConnectionError()
                    return@withContext newAccessToken
                }
            } else {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Failed to refresh access token: code=${response.code} body=${responseBody.take(200)}")
                // Check if token is expired or revoked
                handleApiError(response.code, responseBody, rh.gs(R.string.google_drive_token_refresh_failed))
            }

            null
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error refreshing access token", e)
            showConnectionError(rh.gs(R.string.google_drive_token_refresh_error, e.message ?: ""))
            null
        }
    }
    
    /**
     * Test Google Drive connection
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken()
                if (accessToken == null) {
                    // Error already shown in getValidAccessToken
                    return@withContext false
                }
                val request = Request.Builder()
                    .url("$DRIVE_API_URL/about?fields=user")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    clearConnectionError()
                    return@withContext true
                } else {
                    // Check if token expired/revoked
                    handleApiError(response.code, "", rh.gs(R.string.google_drive_connection_test_failed))
                    return@withContext false
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error testing Google Drive connection", e)
                showConnectionError(rh.gs(R.string.google_drive_connect_error, e.message ?: ""))
                false
            }
        }
    }
    
    /**
     * List folders in Google Drive
     */
    suspend fun listFolders(parentId: String = "root"): List<DriveFolder> {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken()
                if (accessToken == null) {
                    // Error already shown in getValidAccessToken
                    return@withContext emptyList()
                }
                val url = "$DRIVE_API_URL/files?q=mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and trashed=false&fields=files(id,name)&supportsAllDrives=true&includeItemsFromAllDrives=true"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    clearConnectionError()
                    val jsonResponse = JSONObject(responseBody)
                    val files = jsonResponse.getJSONArray("files")
                    val folders = mutableListOf<DriveFolder>()
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        folders.add(DriveFolder(id = file.getString("id"), name = file.getString("name")))
                    }
                    return@withContext folders
                } else {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX List folders failed: ${response.code} ${response.message} body=${responseBody}")
                    // Check if token expired/revoked
                    handleApiError(response.code, responseBody, rh.gs(R.string.google_drive_list_folders_failed))
                    return@withContext emptyList()
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error listing Google Drive folders", e)
                showConnectionError(rh.gs(R.string.google_drive_list_folders_error, e.message ?: ""))
                emptyList()
            }
        }
    }
    
    /**
     * Create folder
     */
    suspend fun createFolder(name: String, parentId: String = "root"): String? {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken() ?: return@withContext null
                aapsLogger.debug(LTag.CORE, "$LOG_PREFIX GDRIVE creating folder: name='$name' parentId=$parentId")
                val metadata = JSONObject().apply {
                    put("name", name)
                    put("mimeType", "application/vnd.google-apps.folder")
                    put("parents", JSONArray().put(parentId))
                }
                
                val requestBody = metadata.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$DRIVE_API_URL/files?fields=id&supportsAllDrives=true")
                    .header("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    clearConnectionError()
                    val jsonResponse = JSONObject(responseBody)
                    val id = jsonResponse.optString("id").takeIf { it.isNotEmpty() }
                    if (id == null) {
                        aapsLogger.error(LTag.CORE, "$LOG_PREFIX GDRIVE Create folder missing id for name='$name' under parent=$parentId body=$responseBody")
                    }else{
                        aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_CREATE_OK name='$name' parent=$parentId id=$id")
                    }
                    return@withContext id
                } else {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX GDRIVE Failed to create folder: $responseBody")
                    return@withContext null
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX GDRIVE Error creating folder", e)
                null
            }
        }
    }
    
    /**
     * Upload file to Google Drive (multipart/related)
     */
    suspend fun uploadFile(fileName: String, fileContent: ByteArray, mimeType: String = "application/octet-stream"): String? {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken()
                if (accessToken == null) {
                    // Error already shown in getValidAccessToken
                    return@withContext null
                }
                debugCurrentUser(accessToken)
                // Resolve target folder: always prefer CloudConstants by file type; fallback to selected folder, then root
                val inferredPath = inferCloudPathFor(fileName)
                val folderId = resolveFolderIdForUpload(inferredPath) ?: return@withContext null
                if (inferredPath != null) {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_START pathHint='$inferredPath' usingFolderId=$folderId file=$fileName size=${fileContent.size} mimeHint=$mimeType")
                } else {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_START noPathHint usingFolderId=$folderId file=$fileName size=${fileContent.size} mimeHint=$mimeType")
                }

                // Metadata JSON body with its own Content-Type
                val metadataJson = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray().put(folderId))
                }.toString()
                val metadataBody = metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())

                // File body with its own Content-Type (guess when needed)
                val effectiveMime = guessMimeType(fileName, mimeType)
                if (effectiveMime != mimeType) aapsLogger.info(LTag.CORE, "$LOG_PREFIX MIME_ADJUST original=$mimeType effective=$effectiveMime file=$fileName")
                val mediaBody = fileContent.toRequestBody(effectiveMime.toMediaType())

                val multipart = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    // OkHttp automatically generates Content-Type header for each part based on RequestBody.contentType();
                    // Manually adding Content-Type will trigger IllegalArgumentException: Unexpected header: Content-Type.
                    .addPart(metadataBody)
                    .addPart(mediaBody)
                    .build()

                val request = Request.Builder()
                    .url("$UPLOAD_URL/files?uploadType=multipart&fields=id&supportsAllDrives=true")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipart)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_RESPONSE code=${response.code} message='${response.message}' hasBody=${responseBodyStr.isNotEmpty()} folderId=$folderId file=$fileName")
                if (responseBodyStr.isNotEmpty()) aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_RESPONSE_BODY ${responseBodyStr.take(500)}")

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBodyStr.ifEmpty { "{}" })
                    val id = jsonResponse.optString("id").takeIf { it.isNotEmpty() }
                    if (id == null) {
                        aapsLogger.error(LTag.CORE, "$LOG_PREFIX UPLOAD_NO_ID folderId=$folderId file=$fileName rawBody='${responseBodyStr.take(200)}'")
                        showConnectionError(rh.gs(R.string.google_drive_upload_no_id))
                        return@withContext null
                    }
                    // Post-upload verification
                    val verified = verifyFileExists(id, accessToken)
                    return@withContext if (verified) {
                        clearConnectionError()
                        aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_OK id=$id file=$fileName folderId=$folderId")
                        logFilePathChain(id, accessToken, "UPLOAD_OK_CHAIN")
                        debugListFolderSnapshot(folderId, accessToken, label = "AFTER_UPLOAD")
                        id
                    } else {
                        aapsLogger.error(LTag.CORE, "$LOG_PREFIX UPLOAD_VERIFY_FAIL id=$id file=$fileName folderId=$folderId")
                        showConnectionError(rh.gs(R.string.google_drive_upload_verify_failed))
                        null
                    }
                } else {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX UPLOAD_FAIL code=${response.code} message='${response.message}' folderId=$folderId body=${responseBodyStr.take(300)}")
                    showConnectionError(rh.gs(R.string.google_drive_upload_failed, response.code.toString()))
                    null
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX EXCEPTION uploadFile file=$fileName", e)
                showConnectionError(rh.gs(R.string.google_drive_upload_error, e.message ?: ""))
                null
            }
        }
    }

    /**
     * Infer default cloud path based on filename (used when no folder is selected).
     */
    private fun inferCloudPathFor(fileName: String): String? {
        val lower = fileName.lowercase(Locale.getDefault())
        return when {
            lower.endsWith(".json") -> CloudConstants.CLOUD_PATH_SETTINGS
            lower.endsWith(".csv") -> CloudConstants.CLOUD_PATH_USER_ENTRIES
            lower.endsWith(".zip") -> CloudConstants.CLOUD_PATH_LOGS
            else -> null
        }
    }

    /** Ensure path always starts with AAPS/ */
    private fun normalizeAapsPath(path: String?): String? {
        if (path.isNullOrBlank()) return path
        val trimmed = path.trim('/', ' ')
        return if (trimmed.startsWith("AAPS/")) trimmed else "AAPS/$trimmed"
    }

    /**
     * Ensure specified cloud path exists; if creation fails, send notification and return null.
     */
    private suspend fun ensureCloudPathOrError(path: String): String? {
        val normalized = normalizeAapsPath(path) ?: path
        if (normalized != path) aapsLogger.info(LTag.CORE, "$LOG_PREFIX ENSURE_PATH_NORMALIZE original='$path' normalized='$normalized'")
        val id = getOrCreateFolderPath(normalized)
        if (id.isNullOrEmpty()) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Unable to ensure cloud path '$normalized'")
            showConnectionError(rh.gs(R.string.google_drive_folder_access_error, normalized))
            return null
        }
        return id
    }

    private suspend fun resolveFolderIdForUpload(pathHint: String?): String? {
        if (!pathHint.isNullOrBlank()) {
            val ensured = ensureCloudPathOrError(pathHint)
            if (ensured != null) {
                val stored = getSelectedFolderId()
                if (stored != ensured) {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_RESOLVE_UPDATE path='$pathHint' newId=$ensured oldId=${stored?.ifEmpty { "<empty>" } ?: "<null>"}")
                    setSelectedFolderId(ensured)
                } else {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_RESOLVE_REUSE path='$pathHint' id=$ensured")
                }
            } else {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX FOLDER_RESOLVE_FAILED path='$pathHint'")
            }
            return ensured
        }

        val stored = getSelectedFolderId()?.ifEmpty { null }
        if (stored != null) {
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_RESOLVE_USE_STORED storedId=$stored pathHint='<none>'")
            return stored
        }

        aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_RESOLVE_DEFAULT_ROOT")
        return "root"
    }

    /**
     * Set selected folder ID
     */
    fun setSelectedFolderId(folderId: String) {
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX SET_SELECTED_FOLDER folderId=$folderId")
        sp.putString(PREF_GOOGLE_DRIVE_FOLDER_ID, folderId)
    }
    
    /**
     * Get selected folder ID
     */
    fun getSelectedFolderId(): String {
        val folderId = sp.getString(PREF_GOOGLE_DRIVE_FOLDER_ID, "")
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX GET_SELECTED_FOLDER folderId='$folderId'")
        return folderId
    }
    
    /**
     * Clear Google Drive related settings
     */
    fun clearGoogleDriveSettings() {
        sp.remove(PREF_GOOGLE_DRIVE_REFRESH_TOKEN)
        sp.remove(PREF_GOOGLE_DRIVE_ACCESS_TOKEN)
        sp.remove(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY)
        sp.remove(PREF_GOOGLE_DRIVE_FOLDER_ID)
        sp.remove("google_drive_code_verifier")
    }
    
    /**
     * Show connection error notification
     */
    private fun showConnectionError(message: String) {
        connectionError = true
        val notificationId = NOTIFICATION_GOOGLE_DRIVE_ERROR
        errorNotificationId = notificationId
        
        val notification = Notification(
            notificationId,
            message,
            Notification.URGENT,
            60
        )
        rxBus.send(EventNewNotification(notification))
        // Notify UI to update cloud storage error state immediately
        rxBus.send(EventCloudStorageStatusChanged())
    }
    
    /**
     * Check if response indicates token expired or revoked
     */
    private fun isTokenExpiredOrRevoked(responseCode: Int, responseBody: String = ""): Boolean {
        return responseCode == 401 || responseCode == 403 ||
            responseBody.contains("invalid_grant") ||
            responseBody.contains("Token has been expired or revoked")
    }
    
    /**
     * Handle API error response and show appropriate error message
     * @param responseCode HTTP response code
     * @param responseBody Response body for additional error detection
     * @param fallbackMessage Message to show when error is not token-related
     */
    private fun handleApiError(responseCode: Int, responseBody: String = "", fallbackMessage: String) {
        if (isTokenExpiredOrRevoked(responseCode, responseBody)) {
            showConnectionError(rh.gs(R.string.cloud_token_expired_or_invalid))
        } else {
            showConnectionError(fallbackMessage)
        }
    }
    

    
    /**
     * Generate code verifier
     */
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    /**
     * Generate code challenge
     */
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
    
    /**
     * Build authorization URL
     */
    private fun buildAuthUrl(codeChallenge: String): String {
        val state = UUID.randomUUID().toString()
        sp.putString("google_drive_oauth_state", state)
        
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
            .toString()
    }
    
    /**
     * Start local HTTP server to receive OAuth callback
     */
    private fun startLocalServer() {
        try {
            // Stop existing server
            stopLocalServer()
            
            // Create new server
            localServer = ServerSocket(REDIRECT_PORT)
            localServer?.soTimeout = 1000  // Set timeout to avoid permanent blocking
            
            // Start server to handle requests
            serverJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    aapsLogger.debug(LTag.CORE, "$LOG_PREFIX Local OAuth server started on port $REDIRECT_PORT")

                    val server = localServer ?: return@launch
                    while (isActive && !server.isClosed) {
                        try {
                            val clientSocket = try { server.accept() } catch (toe: java.net.SocketTimeoutException) { null }
                            clientSocket?.let { socket ->
                                launch { handleHttpRequest(socket) }
                            }
                        } catch (e: Exception) {
                            if (!server.isClosed) {
                                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error accepting connection", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    val server = localServer
                    if (server != null && !server.isClosed) {
                        aapsLogger.error(LTag.CORE, "$LOG_PREFIX Server error", e)
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Failed to start local OAuth server", e)
            throw e
        }
    }
    
    /**
     * Stop local HTTP server
     */
    private fun stopLocalServer() {
        try {
            serverJob?.cancel()
            localServer?.close()
            localServer = null
            aapsLogger.debug(LTag.CORE, "$LOG_PREFIX Local OAuth server stopped")
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error stopping server", e)
        }
    }
    
    /**
     * Handle HTTP request
     */
    private suspend fun handleHttpRequest(socket: Socket) {
        try {
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream()
            
            // Read HTTP request
            val requestLine = input.readLine()
            if (requestLine == null) {
                socket.close()
                return
            }
            
            aapsLogger.debug(LTag.CORE, "$LOG_PREFIX HTTP Request: $requestLine")
            
            // Parse request path
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                val path = parts[1]
                if (path.startsWith("/oauth/callback")) {
                    handleOAuthCallback(path, output)
                } else {
                    sendHttpResponse(output, 404, "Not Found")
                }
            } else {
                sendHttpResponse(output, 400, "Bad Request")
            }
            
            socket.close()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error handling HTTP request", e)
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }
    
    /**
     * Handle OAuth callback
     */
    private suspend fun handleOAuthCallback(path: String, output: OutputStream) {
        try {
            // Parse query parameters
            val queryIndex = path.indexOf('?')
            val params = if (queryIndex >= 0) {
                parseQueryString(path.substring(queryIndex + 1))
            } else {
                emptyMap()
            }
            
            val code = params["code"]
            val state = params["state"]
            val error = params["error"]
            
            aapsLogger.debug(LTag.CORE, "$LOG_PREFIX OAuth callback received - code: ${code != null}, state: $state, error: $error")
            
            if (error != null) {
                sendHttpResponse(output, 400, "OAuth error: $error")
                return
            }
            
            if (code != null && state != null) {
                // Verify state
                val savedState = sp.getString("google_drive_oauth_state", "")
                if (state == savedState) {
                    authCodeReceived = code
                    authState = state
                    sendHttpResponse(output, 200, "Authorization successful! You can close this window.")
                } else {
                    sendHttpResponse(output, 400, "Invalid state parameter")
                }
            } else {
                sendHttpResponse(output, 400, "Missing code or state parameter")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error handling OAuth callback", e)
            sendHttpResponse(output, 500, "Internal server error")
        } finally {
            // Delay closing server
            CoroutineScope(Dispatchers.IO).launch {
                delay(2000) // Wait 2 seconds for response to complete
                stopLocalServer()
            }
        }
    }
    
    /**
     * Parse query string
     */
    private fun parseQueryString(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        
        return query.split("&").associate { param ->
            val keyValue = param.split("=", limit = 2)
            val key = keyValue[0]
            val value = if (keyValue.size > 1) keyValue[1] else ""
            key to value
        }
    }
    
    /**
     * Send HTTP response
     */
    private fun sendHttpResponse(output: OutputStream, statusCode: Int, message: String) {
        try {
            val statusText = when (statusCode) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                500 -> "Internal Server Error"
                else -> "Unknown"
            }
            val autoCloseScript = if (statusCode == 200) """
                <script>
                    (function() {
                        function tryClose() {
                            try { window.close(); } catch (e) {}
                            if (!window.closed) {
                                try { window.open('', '_self'); window.close(); } catch (e) {}
                            }
                            if (!window.closed) {
                                try { history.go(-1); } catch (e) {}
                            }
                            if (!window.closed) {
                                try { location.replace('about:blank'); } catch (e) {}
                            }
                        }
                        function scheduleRetries() {
                            tryClose();
                            setTimeout(tryClose, 300);
                            setTimeout(tryClose, 800);
                            setTimeout(tryClose, 1500);
                            // Retry more times to cover various browser protection mechanisms
                            setTimeout(tryClose, 2500);
                            var count = 0;
                            var iv = setInterval(function() {
                                if (count++ > 5 || window.closed) { clearInterval(iv); return; }
                                tryClose();
                            }, 700);
                        }
                        // Also try to close when page visibility changes (avoid returning to browser after focus switch)
                        document.addEventListener('visibilitychange', function() {
                            if (!document.hidden) {
                                scheduleRetries();
                            }
                        });
                        // Try immediately on load
                        if (document.readyState === 'complete') {
                            scheduleRetries();
                        } else {
                            window.addEventListener('load', scheduleRetries);
                        }
                    })();
                </script>
            """ else ""
            val metaRefresh = if (statusCode == 200) "<meta http-equiv=\"refresh\" content=\"2;url=about:blank\">" else ""
            val className = if (statusCode == 200) "success" else "error"
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>AAPS Google Drive Authorization</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    $metaRefresh
                    <style>
                        body { font-family: Arial, sans-serif; text-align: center; margin-top: 50px; }
                        .success { color: green; }
                        .error { color: red; }
                    </style>
                </head>
                <body>
                    <h1>AAPS Google Drive Authorization</h1>
                    <p class="$className">$message</p>
                    $autoCloseScript
                </body>
                </html>
            """.trimIndent()
            val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                          "Content-Type: text/html; charset=UTF-8\r\n" +
                          "Content-Length: ${htmlContent.toByteArray().size}\r\n" +
                          "Connection: close\r\n" +
                          "\r\n" +
                          htmlContent
            output.write(response.toByteArray())
            output.flush()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error sending HTTP response", e)
        }
    }
    
    /**
     * Wait and get authorization code
     */
    suspend fun waitForAuthCode(timeoutMs: Long = 60000): String? {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            while (authCodeReceived == null && System.currentTimeMillis() - startTime < timeoutMs) {
                delay(500)
            }
            
            val code = authCodeReceived
            authCodeReceived = null // Clear used authorization code
            authState = null
            
            code
        }
    }
    
    /**
     * Check if there is a connection error
     */
    fun hasConnectionError(): Boolean {
        val storageType = sp.getString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, STORAGE_TYPE_LOCAL)
        return storageType == STORAGE_TYPE_GOOGLE_DRIVE && connectionError
    }
    
    /**
     * Clear connection error state
     */
    fun clearConnectionError() {
        connectionError = false
        errorNotificationId?.let { id ->
            // TODO: Implement logic to clear notification
        }
        errorNotificationId = null
        // Notify UI to update cloud storage error state immediately
        rxBus.send(EventCloudStorageStatusChanged())
    }

    /**
     * List settings files (json) in current folder
     */
    suspend fun listSettingsFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext emptyList()
            val folderId = getSelectedFolderId()?.ifEmpty { "root" } ?: "root"
            val query = "'$folderId' in parents and trashed=false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$DRIVE_API_URL/files?q=$encodedQuery&fields=files(id,name,modifiedTime,mimeType)&pageSize=50&supportsAllDrives=true&includeItemsFromAllDrives=true"
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX LIST_SETTINGS_FILES_START folderId=$folderId query='$query' encodedQuery=$encodedQuery")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX FOLDER_LIST_FAIL folderId=$folderId code=${response.code} body=${body.take(300)}")
                showConnectionError(rh.gs(R.string.google_drive_list_settings_failed))
                return@withContext emptyList()
            }
            clearConnectionError()
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX LIST_SETTINGS_FILES_OK count=${body.length} folderId=$folderId")
            val json = JSONObject(body)
            val arr = json.optJSONArray("files") ?: JSONArray()
            val result = mutableListOf<DriveFile>()
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val mimeType = f.optString("mimeType", "")
                // Filter out folders
                if (mimeType != "application/vnd.google-apps.folder") {
                    result.add(DriveFile(id = f.getString("id"), name = f.getString("name")))
                }
            }
            result
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error listing settings files", e)
            showConnectionError(rh.gs(R.string.google_drive_list_settings_error, e.message ?: ""))
            emptyList()
        }
    }

    /**
     * Download file content
     */
    suspend fun downloadFile(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext null
            val request = Request.Builder()
                .url("$DRIVE_API_URL/files/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val msg = response.body?.string()
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Failed to download file: ${msg}")
                showConnectionError(rh.gs(R.string.google_drive_download_failed))
                return@withContext null
            }
            clearConnectionError()
            response.body?.bytes()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error downloading file", e)
            showConnectionError(rh.gs(R.string.google_drive_download_error, e.message ?: ""))
            null
        }
    }

    /**
     * List settings files (json) in current folder with pagination
     */
    suspend fun listSettingsFilesPaged(pageToken: String? = null, pageSize: Int = 10): DriveFilePage = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext DriveFilePage(emptyList(), null)
            val folderId = getSelectedFolderId().ifEmpty { "root" }
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX LIST_SETTINGS_PAGED folderId='$folderId' pageToken=$pageToken pageSize=$pageSize")
            val query = "'$folderId' in parents and trashed=false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val base = StringBuilder()
                .append("$DRIVE_API_URL/files?q=").append(encodedQuery)
                .append("&fields=files(id,name,modifiedTime,mimeType),nextPageToken")
                .append("&pageSize=").append(pageSize)
                .append("&supportsAllDrives=true&includeItemsFromAllDrives=true")
            if (!pageToken.isNullOrEmpty()) base.append("&pageToken=").append(pageToken)
            val request = Request.Builder()
                .url(base.toString())
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Failed to list settings files (paged): $body")
                showConnectionError(rh.gs(R.string.google_drive_list_settings_failed))
                return@withContext DriveFilePage(emptyList(), null)
            }
            clearConnectionError()
            val json = JSONObject(body)
            val arr = json.optJSONArray("files") ?: JSONArray()
            val result = mutableListOf<DriveFile>()
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val mimeType = f.optString("mimeType", "")
                // Filter out folders
                if (mimeType != "application/vnd.google-apps.folder") {
                    result.add(DriveFile(id = f.getString("id"), name = f.getString("name")))
                }
            }
            val next = json.optString("nextPageToken", "").ifEmpty { null }
            DriveFilePage(result, next)
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error listing settings files (paged)", e)
            showConnectionError(rh.gs(R.string.google_drive_list_settings_error, e.message ?: ""))
            DriveFilePage(emptyList(), null)
        }
    }
    
    /**
     * Count total settings files matching yyyy-MM-dd_HHmmss*.json pattern in current folder
     */
    suspend fun countSettingsFiles(): Int = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext 0
            val folderId = getSelectedFolderId().ifEmpty { "root" }
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX COUNT_SETTINGS_FILES folderId='$folderId'")
            val query = "'$folderId' in parents and trashed=false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val namePattern = Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{6}.*\\.json$", RegexOption.IGNORE_CASE)
            
            var totalCount = 0
            var pageToken: String? = null
            
            do {
                val base = StringBuilder()
                    .append("$DRIVE_API_URL/files?q=").append(encodedQuery)
                    .append("&fields=files(name,mimeType),nextPageToken")
                    .append("&pageSize=100")  // Use larger page size for counting
                    .append("&supportsAllDrives=true&includeItemsFromAllDrives=true")
                if (!pageToken.isNullOrEmpty()) base.append("&pageToken=").append(pageToken)
                
                val request = Request.Builder()
                    .url(base.toString())
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX Failed to count settings files: $body")
                    return@withContext 0
                }
                
                val json = JSONObject(body)
                val arr = json.optJSONArray("files") ?: JSONArray()
                
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    val mimeType = f.optString("mimeType", "")
                    val name = f.optString("name", "")
                    // Filter out folders and count only matching files
                    if (mimeType != "application/vnd.google-apps.folder" && namePattern.containsMatchIn(name)) {
                        totalCount++
                    }
                }
                
                pageToken = json.optString("nextPageToken", "").ifEmpty { null }
            } while (pageToken != null)
            
            totalCount
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error counting settings files", e)
            0
        }
    }

    /**
     * Get or create multi-level folders (expressed as path), return final folder ID.
     * All paths are automatically prefixed with "AAPS/" if not already present.
     * Example: path = "export/preferences" -> creates "AAPS/export/preferences"
     * 
     * Special handling for "AAPS" folder: Always reuses existing AAPS folder if one exists in root.
     */
    suspend fun getOrCreateFolderPath(path: String, baseParentId: String = "root"): String? = withContext(Dispatchers.IO) {
        try {
            // Normalize path to always start with AAPS/
            val normalizedPath = normalizeAapsPath(path) ?: return@withContext null
            var trimmed = normalizedPath.trim('/',' ')

            val segments = trimmed.split('/').filter { it.isNotBlank() }
            var currentParentId = baseParentId
            val accumulated = mutableListOf<String>()

            for ((index, seg) in segments.withIndex()) {
                accumulated.add(seg)
                val currentPath = accumulated.joinToString("/")
                val parentPath = accumulated.dropLast(1).joinToString("/")
                val cachedId = pathCache[currentPath]
                if (cachedId != null) {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_SEGMENT_CACHE_HIT level=${index + 1} path='/${currentPath}' id=$cachedId")
                    currentParentId = cachedId
                    continue
                }

                val parentDisplay = if (parentPath.isEmpty()) "/" else "/$parentPath"
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_SEGMENT_CHECK level=${index + 1} name='$seg' parentPath='$parentDisplay' parentId=$currentParentId fullPath='/$currentPath'")

                val existingId = findFolderIdByName(seg, currentParentId)
                
                val resolvedId = existingId ?: createFolder(seg, currentParentId) ?: run {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX FOLDER_SEGMENT_CREATE_FAIL name='$seg' parentPath='$parentDisplay' parentId=$currentParentId requested='/$currentPath'")
                    return@withContext null
                }

                if (existingId != null) {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_SEGMENT_EXIST level=${index + 1} name='$seg' id=$resolvedId fullPath='/$currentPath'")
                } else {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_SEGMENT_CREATED level=${index + 1} name='$seg' id=$resolvedId fullPath='/$currentPath'")
                }

                pathCache[currentPath] = resolvedId
                currentParentId = resolvedId
            }

            aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_PATH_READY path='$path' finalId=$currentParentId")
            pathCache[trimmed] = currentParentId
            currentParentId
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error ensuring folder path $path", e)
            null
        }
    }

    /**
     * Find subfolder by name under specified parent, return its ID if exists; otherwise return null.
     */
    private suspend fun findFolderIdByName(name: String, parentId: String): String? = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext null
            val query = "mimeType='application/vnd.google-apps.folder' and name='$name' and '$parentId' in parents and trashed=false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$DRIVE_API_URL/files?q=$encodedQuery&fields=files(id,name)&pageSize=1&supportsAllDrives=true&includeItemsFromAllDrives=true"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(body)
            val arr = json.optJSONArray("files") ?: JSONArray()
            if (arr.length() == 0) return@withContext null
            arr.getJSONObject(0).getString("id")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Upload file to specified cloud path (automatically creates folders).
     */
    suspend fun uploadFileToPath(fileName: String, fileContent: ByteArray, mimeType: String, path: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_REQUESTED path='$path' file=$fileName size=${fileContent.size}")
                val folderId = resolveFolderIdForUpload(path) ?: run {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX Cannot resolve target path '$path'")
                    showConnectionError("Cannot create destination path")
                    return@withContext null
                }
                val accessToken = getValidAccessToken() ?: return@withContext null
                try {
                    debugCurrentUser(accessToken)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX DEBUG_USER_FAILED (non-critical)", e)
                }
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_START pathHint='$path' folderId=$folderId file=$fileName size=${fileContent.size} mimeHint=$mimeType")

                val metadataJson = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray().put(folderId))
                }.toString()
                val metadataBody = metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                val effectiveMime = guessMimeType(fileName, mimeType)
                if (effectiveMime != mimeType) aapsLogger.info(LTag.CORE, "$LOG_PREFIX MIME_ADJUST original=$mimeType effective=$effectiveMime file=$fileName")
                val mediaBody = fileContent.toRequestBody(effectiveMime.toMediaType())

                val multipart = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    // Same as above, remove custom Content-Type header, let OkHttp automatically add based on body.
                    .addPart(metadataBody)
                    .addPart(mediaBody)
                    .build()

                val request = Request.Builder()
                    .url("$UPLOAD_URL/files?uploadType=multipart&fields=id&supportsAllDrives=true")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipart)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_RESPONSE code=${response.code} message='${response.message}' hasBody=${responseBodyStr.isNotEmpty()} path='$path' file=$fileName")
                if (responseBodyStr.isNotEmpty()) aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_RESPONSE_BODY ${responseBodyStr.take(500)}")
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBodyStr.ifEmpty { "{}" })
                    val id = jsonResponse.optString("id").takeIf { it.isNotEmpty() }
                    if (id == null) {
                        aapsLogger.error(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_NO_ID path='$path' file=$fileName rawBody='${responseBodyStr.take(200)}'")
                        showConnectionError("Upload succeeded but no id returned")
                        return@withContext null
                    }
                    val verified = verifyFileExists(id, accessToken)
                    return@withContext if (verified) {
                        clearConnectionError()
                        aapsLogger.info(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_OK id=$id path='$path' file=$fileName")
                        logFilePathChain(id, accessToken, "UPLOAD_PATH_OK_CHAIN")
                        debugListFolderSnapshot(folderId, accessToken, label = "AFTER_UPLOAD_PATH")
                        id
                    } else {
                        aapsLogger.error(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_VERIFY_FAIL id=$id path='$path' file=$fileName")
                        showConnectionError("Upload verification failed")
                        null
                    }
                } else {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX UPLOAD_PATH_FAIL path='$path' code=${response.code} message='${response.message}' body=${responseBodyStr.take(300)}")
                    showConnectionError("Upload failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX EXCEPTION uploadFileToPath path='$path' file=$fileName", e)
                showConnectionError("Error uploading file: ${e.message}")
                null
            }
        }
    }

    /**
     * Infer MIME type for common file extensions, avoid using imprecise octet-stream.
     */
    private fun guessMimeType(fileName: String, provided: String?): String {
        val prov = provided?.trim().orEmpty()
        if (prov.isNotEmpty() && prov != "application/octet-stream") return prov
        val lower = fileName.lowercase(Locale.getDefault())
        return when {
            lower.endsWith(".json") -> "application/json; charset=UTF-8"
            lower.endsWith(".csv") -> "text/csv; charset=UTF-8"
            lower.endsWith(".zip") -> "application/zip"
            else -> if (prov.isNotEmpty()) prov else "application/octet-stream"
        }
    }

    /**
     * Verify if file actually exists (immediately call Drive API to retrieve metadata).
     * If retrieval fails or file is marked as trashed, verification is considered failed.
     */
    private fun verifyFileExists(fileId: String, accessToken: String): Boolean {
        return try {
            val req = Request.Builder()
                .url("$DRIVE_API_URL/files/$fileId?fields=id,name,parents,mimeType,trashed,webViewLink,createdTime,modifiedTime,size&supportsAllDrives=true")
                .header("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX VERIFY_FAIL id=$fileId code=${resp.code} body='${bodyStr.take(300)}'")
                    return false
                }
                val json = JSONObject(bodyStr)
                val trashed = json.optBoolean("trashed", false)
                if (trashed) {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX VERIFY_FAIL_TRASHED id=$fileId json='${bodyStr.take(200)}'")
                    return false
                }
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX VERIFY_OK id=$fileId name=${json.optString("name")} parents=${json.optJSONArray("parents")?.toString() ?: "[]"} mime=${json.optString("mimeType")} size=${json.optLong("size", -1)} webViewLink=${json.optString("webViewLink")} created=${json.optString("createdTime")} modified=${json.optString("modifiedTime")}")
                true
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX VERIFY_EXCEPTION id=$fileId", e)
            false
        }
    }

    private fun debugCurrentUser(accessToken: String) {
        try {
            val req = Request.Builder()
                .url("$DRIVE_API_URL/about?fields=user(emailAddress,displayName)&supportsAllDrives=true")
                .header("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    runCatching { JSONObject(body).optJSONObject("user") }.getOrNull()?.let { u ->
                        aapsLogger.info(LTag.CORE, "$LOG_PREFIX USER email=${u.optString("emailAddress")} display=${u.optString("displayName")}")
                    }
                } else {
                    aapsLogger.info(LTag.CORE, "$LOG_PREFIX USER_FAIL code=${resp.code}")
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX USER_EXCEPTION", e)
        }
    }

    private fun debugListFolderSnapshot(folderId: String, accessToken: String, label: String) {
        try {
            val query = "'$folderId' in parents and trashed=false"
            val url = "$DRIVE_API_URL/files?q=${Uri.encode(query)}&fields=files(id,name,mimeType,modifiedTime),nextPageToken&pageSize=20&supportsAllDrives=true&includeItemsFromAllDrives=true"
            val req = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    aapsLogger.debug(LTag.CORE, "$LOG_PREFIX FOLDER_LIST_FAIL_DEBUG (non-critical) folderId=$folderId code=${resp.code}")
                    return
                }
                val json = JSONObject(body)
                val arr = json.optJSONArray("files") ?: JSONArray()
                val summary = StringBuilder()
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    summary.append(f.optString("name")).append('(').append(f.optString("id")).append(") ")
                }
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX FOLDER_SNAPSHOT label=$label folderId=$folderId items=${arr.length()} list='${summary.toString().trim()}'")
            }
        } catch (e: Exception) {
            aapsLogger.debug(LTag.CORE, "$LOG_PREFIX FOLDER_SNAPSHOT_EXCEPTION (non-critical) folderId=$folderId: ${e.message}")
        }
    }

    private fun logFilePathChain(fileId: String, accessToken: String, tag: String) {
        try {
            val chain = mutableListOf<String>()
            var currentId: String? = fileId
            var safety = 0
            var reachedRoot = false
            var abort = false
            while (currentId != null && safety++ < 12 && !abort) {
                val req = Request.Builder()
                    .url("$DRIVE_API_URL/files/$currentId?fields=id,name,parents&supportsAllDrives=true")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        aapsLogger.debug(LTag.CORE, "$LOG_PREFIX PATH_CHAIN_FAIL_DEBUG (non-critical) id=$currentId code=${resp.code} partial='${chain.joinToString("/")}'")
                        abort = true
                    }
                    if (!abort) {
                        val json = JSONObject(body)
                        chain.add(json.optString("name"))
                        val parentsArr = json.optJSONArray("parents")
                        currentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.getString(0) else null
                        if (currentId == "root") {
                            chain.add("root")
                            reachedRoot = true
                        }
                    }
                }
                if (reachedRoot) break
            }
            val status = if (reachedRoot) "COMPLETE" else if (abort) "ABORT" else "PARTIAL"
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX $tag status=$status chain='${chain.joinToString("/")}' depth=${chain.size}")
        } catch (e: Exception) {
            aapsLogger.debug(LTag.CORE, "$LOG_PREFIX PATH_CHAIN_EXCEPTION (non-critical) fileId=$fileId: ${e.message}")
        }
    }
}

/**
 * Google Drive folder data class
 */
data class DriveFolder(
    val id: String,
    val name: String
)

/** Google Drive file data class */
data class DriveFile(
    val id: String,
    val name: String
)

/** Paginated result data class */
data class DriveFilePage(
    val files: List<DriveFile>,
    val nextPageToken: String?,
    val totalCount: Int? = null  // Optional total count of files matching pattern
)
