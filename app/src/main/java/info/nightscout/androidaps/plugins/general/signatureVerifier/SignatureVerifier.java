package info.nightscout.androidaps.plugins.general.signatureVerifier;

import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.SP;

/**
 * AndroidAPS is meant to be build by the user.
 * In case someone decides to leak a ready-to-use APK nonetheless, we can still disable it.
 * Self-compiled APKs with privately held certificates cannot and will not be disabled.
 */
public class SignatureVerifier extends PluginBase implements ConstraintsInterface {

    private static final String REVOKED_CERTS_URL = "https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/app/src/main/assets/revoked_certs.txt";
    private static final long UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(1);

    private static SignatureVerifier plugin = new SignatureVerifier();

    private Logger log = LoggerFactory.getLogger(L.CORE);
    private final Object $lock = new Object[0];
    private File revokedCertsFile;
    private List<byte[]> revokedCerts;

    public static SignatureVerifier getPlugin() {
        return plugin;
    }

    private SignatureVerifier() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.signature_verifier));
    }

    @Override
    protected void onStart() {
        super.onStart();
        revokedCertsFile = new File(MainApp.instance().getFilesDir(), "revoked_certs.txt");
        new Thread(() -> {
            loadLocalRevokedCerts();
            if (shouldDownloadCerts()) {
                try {
                    downloadAndSaveRevokedCerts();
                } catch (IOException e) {
                    log.error("Could not download revoked certs", e);
                }
            }
            if (hasIllegalSignature()) showNotification();
        }).start();
    }

    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {
        if (hasIllegalSignature()) {
            showNotification();
            value.set(false);
        }
        if (shouldDownloadCerts()) {
            new Thread(() -> {
                try {
                    downloadAndSaveRevokedCerts();
                } catch (IOException e) {
                    log.error("Could not download revoked certs", e);
                }
            }).start();
        }
        return value;
    }

    private void showNotification() {
        Notification notification = new Notification(Notification.INVALID_VERSION, MainApp.gs(R.string.running_invalid_version), Notification.URGENT);
        RxBus.INSTANCE.send(new EventNewNotification(notification));
    }

    private boolean hasIllegalSignature() {
        try {
            synchronized ($lock) {
                if (revokedCerts == null) return false;
                Signature[] signatures = MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), PackageManager.GET_SIGNATURES).signatures;
                if (signatures != null) {
                    for (Signature signature : signatures) {
                        MessageDigest digest = MessageDigest.getInstance("SHA256");
                        byte[] fingerprint = digest.digest(signature.toByteArray());
                        for (byte[] cert : revokedCerts) {
                            if (Arrays.equals(cert, fingerprint)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            log.error("Error in SignatureVerifier", e);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error in SignatureVerifier", e);
        }
        return false;
    }

    private boolean shouldDownloadCerts() {
        return System.currentTimeMillis() - SP.getLong(R.string.key_last_revoked_certs_check, 0L) >= UPDATE_INTERVAL;
    }

    private void downloadAndSaveRevokedCerts() throws IOException {
        String download = downloadRevokedCerts();
        saveRevokedCerts(download);
        SP.putLong(R.string.key_last_revoked_certs_check, System.currentTimeMillis());
        synchronized ($lock) {
            revokedCerts = parseRevokedCertsFile(download);
        }
    }

    private void loadLocalRevokedCerts() {
        try {
            String revokedCerts = readCachedDownloadedRevokedCerts();
            if (revokedCerts == null) revokedCerts = readRevokedCertsInAssets();
            synchronized ($lock) {
                this.revokedCerts = parseRevokedCertsFile(revokedCerts);
            }
        } catch (IOException e) {
            log.error("Error in SignatureVerifier", e);
        }
    }

    private void saveRevokedCerts(String revokedCerts) throws IOException {
        OutputStream outputStream = new FileOutputStream(revokedCertsFile);
        outputStream.write(revokedCerts.getBytes(StandardCharsets.UTF_8));
        outputStream.close();
    }

    private String downloadRevokedCerts() throws IOException {
        URLConnection connection = new URL(REVOKED_CERTS_URL).openConnection();
        return readInputStream(connection.getInputStream());
    }

    private String readInputStream(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            baos.flush();
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            inputStream.close();
        }
    }

    private String readRevokedCertsInAssets() throws IOException {
        InputStream inputStream = MainApp.instance().getAssets().open("revoked_certs.txt");
        return readInputStream(inputStream);
    }

    private String readCachedDownloadedRevokedCerts() throws IOException {
        if (!revokedCertsFile.exists()) return null;
        return readInputStream(new FileInputStream(revokedCertsFile));
    }

    private List<byte[]> parseRevokedCertsFile(String file) {
        List<byte[]> revokedCerts = new ArrayList<>();
        for (String line : file.split("\n")) {
            if (line.startsWith("#")) continue;
            revokedCerts.add(Hex.decode(line.replace(" ", "").replace(":", "")));
        }
        return revokedCerts;
    }
}
