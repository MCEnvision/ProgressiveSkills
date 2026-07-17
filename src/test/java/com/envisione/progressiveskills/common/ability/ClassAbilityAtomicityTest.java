package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.classdef.ClassCanonicalCodec;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassDefinition;
import com.envisione.progressiveskills.common.classdef.ClassEntitlementGrant;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.classdef.ClassSlotDefinition;
import com.envisione.progressiveskills.common.classdef.ClassSwapPolicy;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.AttributeProjectionSafety;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassAbilityAtomicityTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000120");
    private static final ResourceLocation SLOT = id("progressiveskills:test_slot");
    private static final ResourceLocation FIRST_CLASS = id("progressiveskills:first_class");
    private static final ResourceLocation SECOND_CLASS = id("progressiveskills:second_class");
    private static final ResourceLocation FIRST_ABILITY = id("progressiveskills:first_passive");
    private static final ResourceLocation SECOND_ABILITY = id("progressiveskills:second_passive");
    private static final ResourceLocation ATTRIBUTE = id("minecraft:generic.armor");
    private static final DefinitionRevision REVISION = new DefinitionRevision(12, "c".repeat(64));

    @Test
    void aggregateAbilityProjectionFailureLeavesTheSecondClassUnselected() {
        Catalogs catalogs = catalogs();
        var service = new ProgressionTransactionService(8, 128, 128, 128, 128, Clock.systemUTC());
        service.restoreAccount(PLAYER, PersistedTransactionState.empty());
        AttributeBoundProjector projector = new AttributeBoundProjector();

        CascadePlan first = combinedSelect(service, catalogs, FIRST_CLASS, "first");
        TransactionResult firstResult = service.execute(
                first, REVISION, projector, new NoopExecutor());

        assertTrue(firstResult.status().committed());
        ProgressionSnapshot beforeSecond = service.snapshot(PLAYER);
        EntitlementKey attributeKey = new EntitlementKey(
                AttributeOperation.ADD_VALUE.targetType(), ATTRIBUTE);
        assertEquals(600L * FixedPoint.SCALE, beforeSecond.projectedValues().get(attributeKey));

        CascadePlan second = combinedSelect(service, catalogs, SECOND_CLASS, "second");
        TransactionResult rejected = service.execute(
                second, REVISION, projector, new NoopExecutor());

        assertFalse(rejected.status().committed());
        ProgressionSnapshot after = service.snapshot(PLAYER);
        assertEquals(beforeSecond.stateRevision(), after.stateRevision());
        assertEquals(beforeSecond.balances(), after.balances());
        assertEquals(beforeSecond.ownership(), after.ownership());
        assertEquals(beforeSecond.paidCosts(), after.paidCosts());
        assertEquals(beforeSecond.projectedValues(), after.projectedValues());
        assertEquals(Set.of(FIRST_CLASS), ClassProgression.selectedClasses(after));
        assertFalse(AbilityProgression.ownedAbilities(after).contains(SECOND_ABILITY));
    }

    private static CascadePlan combinedSelect(
            ProgressionTransactionService service,
            Catalogs catalogs,
            ResourceLocation classId,
            String key
    ) {
        ProgressionSnapshot snapshot = service.snapshot(PLAYER);
        CascadePlan primary = ClassProgression.select(
                PLAYER,
                PLAYER,
                catalogs.classes(),
                catalogs.skills(),
                snapshot,
                REVISION,
                classId,
                new IdempotencyKey("phase12/test/atomic/" + key),
                ProgressionCause.GAMEPLAY
        );
        ProgressionSnapshot postPrimary = service.previewSnapshot(primary, REVISION);
        return AbilityProgression.appendReconciliation(
                primary, catalogs.abilities(), postPrimary, REVISION);
    }

    private static Catalogs catalogs() {
        Provenance provenance = new Provenance(id("progressiveskills:test_pack"), "memory", "test");
        ClassSlotDefinition slot = new ClassSlotDefinition(
                SLOT, presentation("Slot"), 2, ClassSwapPolicy.ALLOWED);
        ClassDefinition firstClass = classDefinition(FIRST_CLASS, FIRST_ABILITY);
        ClassDefinition secondClass = classDefinition(SECOND_CLASS, SECOND_ABILITY);
        AbilityDefinition firstAbility = passive(FIRST_ABILITY);
        AbilityDefinition secondAbility = passive(SECOND_ABILITY);
        CanonicalIr ir = CanonicalIr.of(List.of(
                ClassCanonicalCodec.encodeSlot(
                        new DefinitionKey(DefinitionKinds.CLASS_SLOT, SLOT),
                        slot, provenance, SourceMap.empty()),
                ClassCanonicalCodec.encodeClass(
                        new DefinitionKey(DefinitionKinds.CLASS, FIRST_CLASS),
                        firstClass, provenance, SourceMap.empty()),
                ClassCanonicalCodec.encodeClass(
                        new DefinitionKey(DefinitionKinds.CLASS, SECOND_CLASS),
                        secondClass, provenance, SourceMap.empty()),
                AbilityCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.ABILITY, FIRST_ABILITY),
                        firstAbility, provenance, SourceMap.empty()),
                AbilityCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.ABILITY, SECOND_ABILITY),
                        secondAbility, provenance, SourceMap.empty())
        ), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);
        ClassCatalog classes = ClassCatalog.from(ir, skills, trees);
        return new Catalogs(skills, classes, AbilityCatalog.from(ir, skills, classes));
    }

    private static ClassDefinition classDefinition(
            ResourceLocation classId,
            ResourceLocation abilityId
    ) {
        return new ClassDefinition(
                classId,
                presentation(classId.getPath()),
                true,
                false,
                SLOT,
                1,
                Set.of(),
                Map.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                true,
                Optional.empty(),
                Optional.empty(),
                List.of(new ClassEntitlementGrant(
                        id(classId + "/ability"), ClassGrantType.ABILITY, abilityId, 1)),
                List.of()
        );
    }

    private static AbilityDefinition passive(ResourceLocation abilityId) {
        return new AbilityDefinition(
                abilityId,
                presentation(abilityId.getPath()),
                true,
                AbilityKind.PASSIVE,
                false,
                false,
                List.of(new AbilityAttributeEffect(
                        id(abilityId + "/armor"),
                        ATTRIBUTE,
                        AttributeOperation.ADD_VALUE,
                        600L * FixedPoint.SCALE
                )),
                List.of(),
                AbilityTargeting.SELF,
                abilityId,
                0,
                1,
                0,
                List.of()
        );
    }

    private static DefinitionPresentation presentation(String name) {
        ComponentSpec text = ComponentSpec.literal(name);
        ResourceLocation barrier = id("minecraft:barrier");
        return new DefinitionPresentation(
                text,
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, barrier, barrier, text),
                Set.of()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(
            SkillCatalog skills,
            ClassCatalog classes,
            AbilityCatalog abilities
    ) {
    }

    private static final class AttributeBoundProjector implements PersistentProjector {
        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            for (ProjectionChange change : changes) {
                for (AttributeOperation operation : AttributeOperation.values()) {
                    if (change.key().targetType().equals(operation.targetType())
                            && change.after().isPresent()
                            && !AttributeProjectionSafety.isWithinBounds(
                            operation, change.after().getAsLong())) {
                        return Optional.of("Attribute projection exceeds its safety bound");
                    }
                }
            }
            return Optional.empty();
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
        }
    }

    private static final class NoopExecutor implements TransitionActionExecutor {
        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(
                UUID targetId,
                TransactionId transactionId,
                TransitionAction action
        ) {
            return ActionExecution.success("executed");
        }
    }
}
