## Revoking leaked APKs
In order to revoke a leaked APK, you need to retrieve the certificate first. This can be done by extracting the file ``META-INF\CERT.RSA`` from the APK. Open a terminal and run ``keytool -printcert -file CERT.RSA`` to get the SHA-256 fingerprint. The ``keytool`` utility is part of every JDK installation.
```
> keytool -printcert -file CERT.RSA
Owner: O=AndroidAPS
Issuer: O=AndroidAPS
Serial number: 30546c5b
Valid from: Wed May 01 16:37:40 CEST 2019 until: Sun Apr 24 16:37:40 CEST 2044
Certificate fingerprints:
         SHA1: C4:EF:80:AD:CD:07:6F:28:B6:2E:8C:AE:C5:54:19:39:2E:E5:15:0D
         SHA256: 51:6D:12:67:4C:27:F4:9B:9F:E5:42:9B:01:B3:98:E4:66:2B:85:B7:A8:DD:70:32:B7:6A:D7:97:9A:0D:97:10
Signature algorithm name: SHA256withRSA
Subject Public Key Algorithm: 2048-bit RSA key
Version: 3
```
Now revoke the certificate by attaching the SHA-256 checksum to ``app/src/main/assets/revoked_certs.txt`` and prepending a comment (starting with ``#``). Finally, push the changes to ``master`` branch to populate them.
```
#Demo certificate
51:6D:12:67:4C:27:F4:9B:9F:E5:42:9B:01:B3:98:E4:66:2B:85:B7:A8:DD:70:32:B7:6A:D7:97:9A:0D:97:10
````
### Demo keystore
You can verify this works by signing an APK with the demo keystore. The  password for both the keystore and the key is ``androidaps``.