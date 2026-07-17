package com.envisione.progressiveskills.common.requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class CompiledRequirement {
    private final RequirementExpression root;
    private final RequirementLimits limits;
    private final List<RequirementDependency> dependencies;

    private CompiledRequirement(
            RequirementExpression root,
            RequirementLimits limits,
            List<RequirementDependency> dependencies
    ) {
        this.root = root;
        this.limits = limits;
        this.dependencies = dependencies;
    }

    public static CompiledRequirement compile(RequirementExpression root) {
        return compile(root, RequirementLimits.CORE);
    }

    public static CompiledRequirement compile(RequirementExpression root, RequirementLimits limits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(limits, "limits");
        var dependencies = new TreeSet<RequirementDependency>();
        int nodes = inspect(root, 1, limits, dependencies);
        if (nodes > limits.maxNodes()) {
            throw new IllegalArgumentException("Requirement node budget exceeded");
        }
        if (dependencies.size() > limits.maxDependencies()) {
            throw new IllegalArgumentException("Requirement dependency budget exceeded");
        }
        return new CompiledRequirement(root, limits, List.copyOf(dependencies));
    }

    public RequirementEvaluation evaluate(RequirementContext context) {
        return evaluate(context, true);
    }

    public RequirementEvaluation evaluate(RequirementContext context, boolean explain) {
        Objects.requireNonNull(context, "context");
        var state = new EvaluationState(context, limits, explain);
        boolean passed = state.evaluate(root);
        return new RequirementEvaluation(
                passed,
                state.operations,
                state.steps,
                passed ? Optional.empty() : Optional.ofNullable(state.firstFailure)
        );
    }

    public List<RequirementDependency> dependencies() {
        return dependencies;
    }

    private static int inspect(
            RequirementExpression expression,
            int depth,
            RequirementLimits limits,
            Set<RequirementDependency> dependencies
    ) {
        if (depth > limits.maxDepth()) {
            throw new IllegalArgumentException("Requirement depth budget exceeded");
        }
        int nodes = 1;
        if (expression instanceof RequirementExpression.All all) {
            for (RequirementExpression child : all.children()) {
                nodes = Math.addExact(nodes, inspect(child, depth + 1, limits, dependencies));
            }
        } else if (expression instanceof RequirementExpression.Any any) {
            for (RequirementExpression child : any.children()) {
                nodes = Math.addExact(nodes, inspect(child, depth + 1, limits, dependencies));
            }
        } else if (expression instanceof RequirementExpression.Not not) {
            nodes = Math.addExact(nodes, inspect(not.child(), depth + 1, limits, dependencies));
        } else if (expression instanceof RequirementExpression.SkillLevel skill) {
            dependencies.add(skill.dependency());
        } else if (expression instanceof RequirementExpression.Currency currency) {
            dependencies.add(currency.dependency());
        }
        if (nodes > limits.maxNodes()) {
            throw new IllegalArgumentException("Requirement node budget exceeded");
        }
        return nodes;
    }

    private static final class EvaluationState {
        private final RequirementContext context;
        private final RequirementLimits limits;
        private final boolean explain;
        private final List<RequirementStep> steps = new ArrayList<>();
        private int operations;
        private String firstFailure;

        private EvaluationState(RequirementContext context, RequirementLimits limits, boolean explain) {
            this.context = context;
            this.limits = limits;
            this.explain = explain;
        }

        private boolean evaluate(RequirementExpression expression) {
            operations++;
            if (operations > limits.maxOperations()) {
                throw new IllegalArgumentException("Requirement operation budget exceeded");
            }
            if (expression instanceof RequirementExpression.All all) {
                for (RequirementExpression child : all.children()) {
                    if (!evaluate(child)) {
                        return false;
                    }
                }
                return true;
            }
            if (expression instanceof RequirementExpression.Any any) {
                String previousFailure = firstFailure;
                for (RequirementExpression child : any.children()) {
                    if (evaluate(child)) {
                        firstFailure = previousFailure;
                        return true;
                    }
                }
                if (firstFailure == null) {
                    firstFailure = "no alternative requirement passed";
                }
                return false;
            }
            if (expression instanceof RequirementExpression.Not not) {
                String previousFailure = firstFailure;
                boolean childPassed = evaluate(not.child());
                if (!childPassed) {
                    firstFailure = previousFailure;
                    return true;
                }
                if (previousFailure == null) {
                    firstFailure = "negated requirement matched";
                }
                return false;
            }
            if (expression instanceof RequirementExpression.SkillLevel skill) {
                return leaf(skill.dependency(), skill.operator(), skill.value(), skill.missing());
            }
            if (expression instanceof RequirementExpression.Currency currency) {
                return leaf(currency.dependency(), currency.operator(), currency.value(), currency.missing());
            }
            throw new IllegalArgumentException("Unknown requirement expression node");
        }

        private boolean leaf(
                RequirementDependency dependency,
                ComparisonOperator operator,
                long expected,
                boolean missingPolicy
        ) {
            RequirementContext.Lookup lookup = context.lookup(dependency);
            boolean passed = lookup.present() ? operator.test(lookup.value(), expected) : missingPolicy;
            String detail = lookup.present()
                    ? dependency.serialized() + " is " + lookup.value() + " and requires "
                    + operator.serializedName() + " " + expected
                    : dependency.serialized() + " is missing and policy is " + missingPolicy;
            if (!passed && firstFailure == null) {
                firstFailure = detail;
            }
            if (explain) {
                if (steps.size() >= limits.maxExplanations()) {
                    throw new IllegalArgumentException("Requirement explanation budget exceeded");
                }
                steps.add(new RequirementStep(
                        dependency,
                        lookup.present(),
                        lookup.value(),
                        operator,
                        expected,
                        passed,
                        detail
                ));
            }
            return passed;
        }
    }
}
