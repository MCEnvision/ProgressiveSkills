package com.envisione.progressiveskills.common.social;

import java.util.Objects;

public record PrivacySettings(
        Visibility profileVisibility,
        boolean shareRole,
        boolean shareBuild,
        boolean shareResources,
        boolean shareCooldowns,
        boolean leaderboardOptIn
) {
    public static final PrivacySettings DEFAULT = new PrivacySettings(
            Visibility.PARTY, true, false, false, false, false);

    public PrivacySettings {
        Objects.requireNonNull(profileVisibility, "profileVisibility");
    }

    public enum Visibility {
        PUBLIC,
        PARTY,
        PRIVATE
    }
}
