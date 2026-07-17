package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;

import java.util.Map;

/** Typed compiler registry for definition kinds implemented by the current engine phase. */
public final class TypedDefinitionCompiler {
    public CanonicalDefinition compile(
            com.envisione.progressiveskills.common.id.DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        CanonicalValue value;
        if (key.kind().equals(DefinitionKinds.COMPONENT_SPEC)) {
            value = new CanonicalValue.ComponentValue(
                    TomlPresentationCompiler.component(fields, key + " component_spec")
            );
        } else if (key.kind().equals(DefinitionKinds.ICON_SPEC)) {
            value = new CanonicalValue.IconValue(
                    TomlPresentationCompiler.icon(fields, key + " icon_spec")
            );
        } else if (key.kind().equals(DefinitionKinds.SKILL)) {
            return SkillTomlCompiler.skill(key, fields, provenance, sourceMap);
        } else if (key.kind().equals(DefinitionKinds.CURRENCY)) {
            return SkillTomlCompiler.currency(key, fields, provenance, sourceMap);
        } else if (key.kind().equals(DefinitionKinds.RULE)) {
            return RuleTomlCompiler.rule(key, fields, provenance, sourceMap);
        } else if (key.kind().equals(DefinitionKinds.TREE)) {
            return TreeTomlCompiler.tree(key, fields, provenance, sourceMap);
        } else if (key.kind().equals(DefinitionKinds.CLASS_SLOT)) {
            return ClassTomlCompiler.slot(key, fields, provenance, sourceMap);
        } else if (key.kind().equals(DefinitionKinds.CLASS)) {
            return ClassTomlCompiler.classDefinition(key, fields, provenance, sourceMap);
        } else if (key.kind().equals(DefinitionKinds.ABILITY)) {
            return AbilityTomlCompiler.ability(key, fields, provenance, sourceMap);
        } else {
            throw new UnsupportedDefinitionSchemaException(
                    "Definition kind " + key.kind().id() + " is reserved but its typed compiler is not implemented yet"
            );
        }
        return new CanonicalDefinition(
                DefinitionHeader.withoutPresentation(SchemaVersion.V2, key),
                new CanonicalValue.ObjectValue(Map.of("value", value)),
                provenance,
                sourceMap
        );
    }

    /** Distinguishes a planned-but-unimplemented schema from malformed content. */
    public static final class UnsupportedDefinitionSchemaException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public UnsupportedDefinitionSchemaException(String message) {
            super(message);
        }
    }
}
