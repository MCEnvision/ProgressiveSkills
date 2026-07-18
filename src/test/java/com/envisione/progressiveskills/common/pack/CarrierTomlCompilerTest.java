package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.carrier.CarrierCanonicalCodec;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierUseAction;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierTomlCompilerTest {
    @Test
    void compilerAcceptsEveryCoreActionAndPreservesAuthoredOrder() {
        var fields = fields();
        fields.put("use_actions", List.of(
                Map.of(
                        "id", "test:carrier/z_xp", "type", "xp",
                        "skill", "test:physique", "amount", 12.5, "consume", 1
                ),
                Map.of(
                        "id", "test:carrier/a_level", "type", "level",
                        "skill", "test:physique", "amount", 2, "consume", 1
                ),
                Map.of(
                        "id", "test:carrier/m_points", "type", "currency",
                        "currency", "test:points", "amount", 4, "consume", 0
                ),
                Map.of(
                        "id", "test:carrier/b_respec", "type", "tree_respec",
                        "tree", "test:tree", "consume", 1
                )
        ));

        var definition = CarrierCanonicalCodec.decode(compile(fields));

        assertEquals(CarrierKind.TOME, definition.carrier());
        assertEquals(
                List.of(
                        id("test:carrier/z_xp"), id("test:carrier/a_level"),
                        id("test:carrier/m_points"), id("test:carrier/b_respec")
                ),
                definition.useActions().stream().map(CarrierUseAction::id).toList()
        );
    }

    @Test
    void compilerRejectsUnregisteredKindsUnknownActionsAndUnboundedValues() {
        var badKind = fields();
        badKind.put("carrier", "test:dynamic_item");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(badKind))
                .getMessage().contains("Unknown Core carrier kind"));

        var command = fields();
        command.put("use_actions", List.of(Map.of(
                "id", "test:carrier/command", "type", "command",
                "amount", 1, "consume", 1
        )));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(command))
                .getMessage().contains("Unknown Core carrier action type"));

        var oversized = fields();
        oversized.put("stack_size", 65);
        assertThrows(IllegalArgumentException.class, () -> compile(oversized));

        var unsafeLive = fields();
        unsafeLive.put("migration_policy", "accept_live");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(unsafeLive))
                .getMessage().contains("rejects unsafe accept_live"));

        var untestedMail = fields();
        untestedMail.put("delivery_policy", "provider_mail");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(untestedMail))
                .getMessage().contains("requires a tested mail provider"));
    }

    private static LinkedHashMap<String, Object> fields() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("display", Map.of("fallback", "Carrier"));
        fields.put("icon", Map.of(
                "type", "item", "value", "minecraft:book",
                "fallback", "minecraft:barrier", "alt", "Book"
        ));
        fields.put("carrier", "progressiveskills:tome");
        fields.put("behavior_version", 2);
        fields.put("charges", 4);
        fields.put("migration_policy", "keep_pinned");
        fields.put("bind", "none");
        fields.put("delivery_policy", "pending_claim");
        fields.put("use_actions", List.of(Map.of(
                "id", "test:carrier/xp", "type", "xp",
                "skill", "test:physique", "amount", 1, "consume", 1
        )));
        return fields;
    }

    private static com.envisione.progressiveskills.common.ir.CanonicalDefinition compile(
            Map<String, Object> fields
    ) {
        return CarrierTomlCompiler.carrier(
                new DefinitionKey(DefinitionKinds.ITEM, id("test:carrier")),
                fields,
                new Provenance(id("test:pack"), "items/carrier.toml", "toml"),
                SourceMap.empty()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
