package com.envisione.progressiveskills.common.requirement;

@FunctionalInterface
public interface RequirementContext {
    Lookup lookup(RequirementDependency dependency);

    record Lookup(boolean present, long value) {
        public static Lookup present(long value) {
            return new Lookup(true, value);
        }

        public static Lookup missing() {
            return new Lookup(false, 0);
        }
    }
}
