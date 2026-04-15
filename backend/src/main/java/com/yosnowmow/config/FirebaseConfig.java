package com.yosnowmow.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Initialises Firebase Admin SDK on application startup.
 *
 * Two FirebaseApp instances are created:
 *   - default  : primary project (yosnowmow-dev / yosnowmow-prod)
 *   - "audit"  : separate audit-log Firestore project (yosnowmow-audit)
 *
 * In emulator mode (yosnowmow.firebase.use-emulator=true in application-dev.yml)
 * the FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST system properties
 * are set programmatically so all Admin SDK calls are routed to the local
 * Firebase Emulator Suite instead of the live cloud projects.
 *
 * Service account JSON files are NEVER committed to Git.
 * In prod they are injected from GCP Secret Manager via Cloud Run --set-secrets.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${yosnowmow.firebase.project-id}")
    private String projectId;

    @Value("${yosnowmow.firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${yosnowmow.firebase.audit-project-id:yosnowmow-audit}")
    private String auditProjectId;

    @Value("${yosnowmow.firebase.audit-service-account-path:}")
    private String auditServiceAccountPath;

    @Value("${yosnowmow.firebase.use-emulator:false}")
    private boolean useEmulator;

    @Value("${yosnowmow.firebase.emulator-host:localhost:8080}")
    private String emulatorHost;

    // ── Default (primary) FirebaseApp ──────────────────────────────────────

    @Bean
    @Primary
    public FirebaseApp firebaseApp() throws IOException {
        if (useEmulator) {
            configureEmulator();
        }

        // Guard: FirebaseApp.initializeApp() throws if called twice (e.g. in tests)
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase default app already initialised — reusing existing instance");
            return FirebaseApp.getInstance();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(buildCredentials(serviceAccountPath, "primary"))
                .setProjectId(projectId)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("Firebase default app initialised for project: {}", projectId);
        return app;
    }

    // ── Audit FirebaseApp ──────────────────────────────────────────────────

    @Bean("auditFirebaseApp")
    public FirebaseApp auditFirebaseApp() throws IOException {
        final String appName = "audit";

        try {
            // Returns existing app if already initialised
            return FirebaseApp.getInstance(appName);
        } catch (IllegalStateException ignored) {
            // App not yet initialised — continue below
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(buildCredentials(auditServiceAccountPath, "audit"))
                .setProjectId(auditProjectId)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options, appName);
        log.info("Firebase audit app initialised for project: {}", auditProjectId);
        return app;
    }

    // ── Convenience beans ──────────────────────────────────────────────────

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    /**
     * Primary Firestore instance (jobs, users, workers, etc.)
     *
     * In emulator mode, builds a Firestore client directly from {@link FirestoreOptions}
     * targeting the local emulator.  This bypasses {@link FirestoreClient#getFirestore(FirebaseApp)},
     * which would rely on the {@code FIRESTORE_EMULATOR_HOST} <em>environment variable</em>
     * (read via {@code System.getenv()}).  Java cannot set environment variables at runtime —
     * {@code System.setProperty()} writes to a different store and is ignored by the SDK.
     */
    @Bean
    @Primary
    public Firestore firestore(FirebaseApp firebaseApp) {
        if (useEmulator) {
            log.info("Firestore (primary): connecting directly to emulator at {}", emulatorHost);
            return FirestoreOptions.newBuilder()
                    .setEmulatorHost(emulatorHost)
                    .setProjectId(projectId)
                    .build()
                    .getService();
        }
        return FirestoreClient.getFirestore(firebaseApp);
    }

    /**
     * Audit Firestore instance — append-only audit log in separate project.
     *
     * In emulator mode, both the primary and audit Firestore clients target the same
     * local emulator instance (the emulator simulates multiple projects via projectId).
     */
    @Bean("auditFirestore")
    public Firestore auditFirestore(@Qualifier("auditFirebaseApp") FirebaseApp auditApp) {
        if (useEmulator) {
            log.info("Firestore (audit): connecting directly to emulator at {} (project: {})",
                    emulatorHost, auditProjectId);
            return FirestoreOptions.newBuilder()
                    .setEmulatorHost(emulatorHost)
                    .setProjectId(auditProjectId)
                    .build()
                    .getService();
        }
        return FirestoreClient.getFirestore(auditApp);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Builds GoogleCredentials for a Firebase app instance.
     *
     * Resolution order:
     * <ol>
     *   <li>If {@code path} is set: load credentials from that service account JSON file.</li>
     *   <li>If emulator mode is active and no path is set: return a static fake credential.
     *       The Firebase Auth and Firestore emulators do not validate credentials — any
     *       non-null token is accepted.  This avoids requiring {@code gcloud auth
     *       application-default login} for local development.</li>
     *   <li>Otherwise (production / Cloud Run): use Application Default Credentials (ADC).
     *       Cloud Run provides ADC automatically via the revision service account identity.</li>
     * </ol>
     */
    private GoogleCredentials buildCredentials(String path, String label) throws IOException {
        if (path != null && !path.isBlank()) {
            log.info("Firebase {} app: loading credentials from {}", label, path);
            try (InputStream is = new FileInputStream(path)) {
                return GoogleCredentials.fromStream(is);
            }
        }
        if (useEmulator) {
            log.info("Firebase {} app: emulator mode — using fake credentials (no ADC needed)", label);
            return GoogleCredentials.create(new AccessToken("emulator-local-dev", null));
        }
        log.info("Firebase {} app: no service account path — using Application Default Credentials", label);
        return GoogleCredentials.getApplicationDefault();
    }

    /**
     * Logs emulator mode activation.
     *
     * <p>Firestore emulator routing is handled programmatically in the {@link #firestore}
     * and {@link #auditFirestore} beans via {@link FirestoreOptions#newBuilder()}.
     * {@code System.setProperty()} is NOT used for Firestore — the Firestore SDK reads
     * {@code FIRESTORE_EMULATOR_HOST} via {@code System.getenv()}, which Java cannot
     * modify at runtime.
     *
     * <p>For FirebaseAuth token verification to accept emulator-issued tokens, the
     * {@code FIREBASE_AUTH_EMULATOR_HOST} environment variable must be set in the
     * OS process before the JVM starts:
     * <pre>
     *   export FIREBASE_AUTH_EMULATOR_HOST=localhost:9099
     *   mvn spring-boot:run
     * </pre>
     * Without it, {@code FirebaseAuth.verifyIdToken()} will reject all tokens and
     * every authenticated endpoint will return 401.  Unauthenticated endpoints
     * (e.g. {@code /api/health}) work regardless.
     */
    private void configureEmulator() {
        log.info("Firebase emulator mode active — Firestore → {} (programmatic), Auth → localhost:9099 (requires env var)",
                emulatorHost);
    }
}
