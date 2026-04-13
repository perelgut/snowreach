package com.yosnowmow.config;

import com.google.auth.oauth2.GoogleCredentials;
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

    /** Primary Firestore instance (jobs, users, workers, etc.) */
    @Bean
    @Primary
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    /** Audit Firestore instance — append-only audit log in separate project */
    @Bean("auditFirestore")
    public Firestore auditFirestore(@Qualifier("auditFirebaseApp") FirebaseApp auditApp) {
        return FirestoreClient.getFirestore(auditApp);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Builds GoogleCredentials from a service account JSON file path.
     * If the path is blank (emulator or local dev without a service account),
     * falls back to Application Default Credentials (ADC).
     * ADC works automatically in Cloud Run and with:
     *   gcloud auth application-default login
     */
    private GoogleCredentials buildCredentials(String path, String label) throws IOException {
        if (path != null && !path.isBlank()) {
            log.info("Firebase {} app: loading credentials from {}", label, path);
            try (InputStream is = new FileInputStream(path)) {
                return GoogleCredentials.fromStream(is);
            }
        }
        log.info("Firebase {} app: no service account path — using Application Default Credentials", label);
        return GoogleCredentials.getApplicationDefault();
    }

    /**
     * Routes all Admin SDK calls to the local emulator by setting the
     * standard system properties checked by the Firebase Admin SDK.
     * Must be called before FirebaseApp.initializeApp().
     */
    private void configureEmulator() {
        log.info("Firebase emulator mode enabled — routing Firestore to {}", emulatorHost);
        System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);
        System.setProperty("FIREBASE_AUTH_EMULATOR_HOST", "localhost:9099");
    }
}
