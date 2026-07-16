package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Exact semantic impact between live and staged snapshots. */
public record SemanticDiff(
        List<DefinitionChange> definitionChanges,
        List<PackChange> packChanges,
        boolean aliasesChanged,
        boolean disabledSetChanged,
        String fromDigest,
        String toDigest
) {
    public SemanticDiff {
        definitionChanges = definitionChanges.stream().sorted().toList();
        packChanges = packChanges.stream().sorted().toList();
    }

    public static SemanticDiff between(PackSnapshot live, PackSnapshot staged) {
        var definitions = new TreeSet<DefinitionKey>();
        definitions.addAll(live.canonicalIr().definitions().keySet());
        definitions.addAll(staged.canonicalIr().definitions().keySet());
        var definitionChanges = new ArrayList<DefinitionChange>();
        for (DefinitionKey key : definitions) {
            var before = live.canonicalIr().definitions().get(key);
            var after = staged.canonicalIr().definitions().get(key);
            ChangeKind kind = before == null ? ChangeKind.ADDED
                    : after == null ? ChangeKind.REMOVED
                    : before.semanticProjection().equals(after.semanticProjection()) ? ChangeKind.UNCHANGED
                    : ChangeKind.MODIFIED;
            if (kind != ChangeKind.UNCHANGED) {
                definitionChanges.add(new DefinitionChange(key, kind));
            }
        }
        var packIds = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        packIds.addAll(live.manifests().keySet());
        packIds.addAll(staged.manifests().keySet());
        var packChanges = new ArrayList<PackChange>();
        for (ResourceLocation id : packIds) {
            PackManifest before = live.manifests().get(id);
            PackManifest after = staged.manifests().get(id);
            ChangeKind kind = before == null ? ChangeKind.ADDED
                    : after == null ? ChangeKind.REMOVED
                    : before.equals(after) ? ChangeKind.UNCHANGED : ChangeKind.MODIFIED;
            if (kind != ChangeKind.UNCHANGED) {
                packChanges.add(new PackChange(id, kind));
            }
        }
        return new SemanticDiff(
                definitionChanges,
                packChanges,
                !live.canonicalIr().aliases().equals(staged.canonicalIr().aliases()),
                !live.disabledDefinitions().equals(staged.disabledDefinitions()),
                live.contentDigest(),
                staged.contentDigest()
        );
    }

    public long definitions(ChangeKind kind) {
        return definitionChanges.stream().filter(change -> change.kind() == kind).count();
    }

    public long packs(ChangeKind kind) {
        return packChanges.stream().filter(change -> change.kind() == kind).count();
    }

    public boolean isEmpty() {
        return definitionChanges.isEmpty() && packChanges.isEmpty() && !aliasesChanged && !disabledSetChanged;
    }

    public enum ChangeKind { ADDED, REMOVED, MODIFIED, UNCHANGED }

    public record DefinitionChange(DefinitionKey key, ChangeKind kind) implements Comparable<DefinitionChange> {
        @Override
        public int compareTo(DefinitionChange other) {
            int keyComparison = key.compareTo(other.key);
            return keyComparison != 0 ? keyComparison : kind.compareTo(other.kind);
        }
    }

    public record PackChange(ResourceLocation id, ChangeKind kind) implements Comparable<PackChange> {
        @Override
        public int compareTo(PackChange other) {
            int idComparison = id.compareNamespaced(other.id);
            return idComparison != 0 ? idComparison : kind.compareTo(other.kind);
        }
    }
}
