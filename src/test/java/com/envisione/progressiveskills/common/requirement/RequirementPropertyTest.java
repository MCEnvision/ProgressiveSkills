package com.envisione.progressiveskills.common.requirement;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirementPropertyTest {
    @Property(tries = 500)
    void everyComparisonMatchesThePrimitiveLongContract(
            @ForAll long actual,
            @ForAll long expected,
            @ForAll ComparisonOperator operator
    ) {
        var expression = new RequirementExpression.SkillLevel(
                ResourceLocation.parse("test:skill"), operator, expected, false
        );
        boolean result = CompiledRequirement.compile(expression).evaluate(
                dependency -> RequirementContext.Lookup.present(actual)
        ).passed();
        assertEquals(operator.test(actual, expected), result);
    }

    @Property(tries = 500)
    void missingPolicyIsTheOnlyResultForUnavailableContext(@ForAll long seed) {
        boolean policy = (seed & 1) == 0;
        var expression = new RequirementExpression.Currency(
                ResourceLocation.parse("test:points"), ComparisonOperator.EQUAL, 0, policy
        );
        boolean result = CompiledRequirement.compile(expression).evaluate(
                dependency -> RequirementContext.Lookup.missing()
        ).passed();
        assertEquals(policy, result);
    }
}
