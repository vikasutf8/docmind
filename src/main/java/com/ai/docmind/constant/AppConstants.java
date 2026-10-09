package com.ai.docmind.constant;

/** Application-wide constants. No instances. */
public final class AppConstants {

    private AppConstants() {
        throw new UnsupportedOperationException("constants only");
    }

    public static final class Api {
        public static final String BASE_PATH = "/api";
        public static final String V1 = BASE_PATH + "/v1";

        private Api() {
            throw new UnsupportedOperationException("constants only");
        }
    }

    public static final class Providers {
        public static final String MYSQL = "mysql";
        public static final String POSTGRES = "pg";
        public static final String MONGO = "mongo";
        public static final String CASSANDRA = "cassandra";

        private Providers() {
            throw new UnsupportedOperationException("constants only");
        }
    }

    public static final class Headers {
        public static final String CORRELATION_ID = "X-Correlation-Id";

        private Headers() {
            throw new UnsupportedOperationException("constants only");
        }
    }

    public static final class Profiles {
        public static final String DEV = "dev";
        public static final String PROD = "prod";

        private Profiles() {
            throw new UnsupportedOperationException("constants only");
        }
    }
}
