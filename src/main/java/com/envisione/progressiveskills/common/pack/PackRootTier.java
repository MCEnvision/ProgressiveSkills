package com.envisione.progressiveskills.common.pack;

/** Locked low-to-high content-pack root precedence. */
public enum PackRootTier {
    ENGINE_FALLBACK,
    MOD_PROVIDED,
    GLOBAL_CONFIG,
    WORLD_OVERLAY,
    STUDIO_OVERLAY,
    RUNTIME_OVERLAY
}
