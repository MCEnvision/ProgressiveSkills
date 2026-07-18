package com.envisione.progressiveskills.common.provider;

import java.util.Locale;

public enum ProviderCapability {
    VANILLA_ATTRIBUTES,
    MODDED_ATTRIBUTES,
    SPELL_OWNERSHIP,
    STAGE_OWNERSHIP,
    PARTY_MEMBERSHIP,
    SHARED_STORAGE,
    CARRIER_SLOTS,
    RECIPE_VIEWER,
    GUIDE_EXPORT,
    SCRIPT_BUILDERS;

    public static ProviderCapability parse(String value) {
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
