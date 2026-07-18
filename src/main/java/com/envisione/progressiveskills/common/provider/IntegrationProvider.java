package com.envisione.progressiveskills.common.provider;

import java.util.Set;

public interface IntegrationProvider {
    String id();

    String version();

    Set<ProviderCapability> capabilities();

    ProviderHealth probe();
}
