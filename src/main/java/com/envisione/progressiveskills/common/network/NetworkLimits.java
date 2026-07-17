package com.envisione.progressiveskills.common.network;

import java.util.Objects;

/** Hard protocol ceilings shared by codecs, transfer assembly, and tests. */
public final class NetworkLimits {
    public static final int PROTOCOL_VERSION = 4;
    public static final String REGISTRAR_VERSION = "4";
    public static final long FEATURE_DEFINITION_PROJECTION = 1L;
    public static final long FEATURE_FULL_STATE = 1L << 1;
    public static final long FEATURE_STATE_DELTA = 1L << 2;
    public static final long FEATURE_BOUNDED_INTENTS = 1L << 3;
    public static final long FEATURE_CORE_TREES = 1L << 4;
    public static final long FEATURE_CORE_CLASSES = 1L << 5;
    public static final long FEATURE_CORE_ABILITIES = 1L << 6;
    public static final long REQUIRED_FEATURES = FEATURE_DEFINITION_PROJECTION
            | FEATURE_FULL_STATE | FEATURE_STATE_DELTA | FEATURE_BOUNDED_INTENTS
            | FEATURE_CORE_TREES | FEATURE_CORE_CLASSES | FEATURE_CORE_ABILITIES;

    public static final int FIXED_ABILITY_SLOTS = 8;
    public static final int MAX_ABILITY_ACTION_SUMMARIES = 32;
    public static final int MAX_ABILITY_COST_SUMMARIES = 16;
    public static final int MAX_ABILITY_REQUIREMENTS = 64;

    public static final int MAX_DEFINITIONS = 4_096;
    public static final int MAX_VISIBLE_VALUES = 4_096;
    public static final int MAX_ALIASES_PER_DEFINITION = 64;
    public static final int MAX_TEXT_BYTES = 8_192;
    public static final int MAX_KEY_BYTES = 512;
    public static final int MAX_INTENT_BYTES = 2_048;
    public static final int MAX_DELTA_BYTES = 65_536;
    public static final int MAX_SERVERBOUND_PAYLOAD_BYTES = 16_384;
    public static final int CHUNK_BYTES = 24_576;
    public static final int MAX_CHUNKS = 128;
    public static final int MAX_COMPRESSED_TRANSFER_BYTES = CHUNK_BYTES * MAX_CHUNKS;
    public static final int MAX_DEFINITION_BYTES = 2_097_152;
    public static final int MAX_STATE_BYTES = 1_048_576;
    public static final int MAX_CONCURRENT_TRANSFERS = 2;
    public static final int MAX_TRANSFER_RETRIES = 1;
    public static final int MAX_REQUEST_RESULTS = 64;
    public static final long MAX_FUTURE_REQUEST_JUMP = 1_024;
    public static final long SESSION_TIMEOUT_MILLIS = 30_000;
    public static final int MAX_RESYNC_REASON_BYTES = 256;
    public static final int MAX_TREE_NODES_PER_VIEW = 64;
    public static final int MAX_TREE_PREREQUISITES = 64;
    public static final int MAX_TREE_MINIMUM_SKILLS = 32;
    public static final int MAX_TREE_PREVIEW_NODES = 64;
    public static final int MAX_TREE_PREVIEW_BALANCES = 64;
    public static final int MAX_TREE_PREVIEW_BLOCKERS = 64;
    public static final int MAX_CLASS_PREREQUISITES = 64;
    public static final int MAX_CLASS_MINIMUM_SKILLS = 32;
    public static final int MAX_CLASS_EXCLUSIVE_TAGS = 32;
    public static final int MAX_CLASS_GRANT_SUMMARIES = 32;
    public static final int MAX_CLASS_SYNERGY_VIEWS = 64;
    public static final int MAX_CLASS_SYNERGY_CLASSES = 16;
    public static final int MAX_CLASS_STARTER_ITEMS = 32;
    public static final int MAX_CLASS_SLOT_CAPACITY = 64;
    public static final int MAX_CLASS_PREVIEW_CLASSES = 64;
    public static final int MAX_CLASS_PREVIEW_BALANCES = 64;
    public static final int MAX_CLASS_PREVIEW_BLOCKERS = 64;

    private NetworkLimits() {
    }

    public static String requireDigest(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
        return value;
    }

    public static String requireBoundedText(String value, int maximumBytes, String name) {
        Objects.requireNonNull(value, name);
        int bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > maximumBytes || value.chars().anyMatch(character ->
                Character.isISOControl(character) && character != '\n' && character != '\t')) {
            throw new IllegalArgumentException(name + " exceeds its safe text contract");
        }
        return value;
    }
}
