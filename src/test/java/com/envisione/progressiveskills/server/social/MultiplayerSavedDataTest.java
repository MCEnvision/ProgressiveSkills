package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.PartyReadiness;
import com.envisione.progressiveskills.common.social.PrivacySettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerSavedDataTest {
    @Test
    void contributionDeliveryResumesAcrossPersistence() {
        var data = new MultiplayerSavedData();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        data.createParty(owner, "Test Party");
        data.invite(owner, member);
        data.acceptInvite(member);
        var shares = new LinkedHashMap<UUID, Long>();
        shares.put(owner, 4L);
        shares.put(member, 6L);
        var receipt = data.shareExact(owner, id("skill"), shares, "Test contribution allocation");
        data.markContributionDelivered(owner, receipt.receiptId(), owner);

        CompoundTag saved = data.save(new CompoundTag(), null);
        MultiplayerSavedData loaded = MultiplayerSavedData.load(saved, null);

        assertEquals(Map.of(member, 6L), loaded.pendingShares(owner, receipt.receiptId()));
        assertEquals(receipt.receiptId(), loaded.shareExact(
                owner, id("skill"), Map.of(owner, 1L), "Resume").receiptId());
        loaded.markContributionDelivered(owner, receipt.receiptId(), member);
        loaded.completeContribution(owner, receipt.receiptId());
        assertThrows(IllegalStateException.class,
                () -> loaded.pendingShares(owner, receipt.receiptId()));
    }

    @Test
    void readinessUsesCurrentPrivacyAtReadTime() {
        var data = new MultiplayerSavedData();
        UUID player = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        data.createParty(player, "Private Party");
        data.invite(player, member);
        data.acceptInvite(member);
        data.setReadiness(player, new PartyReadiness(
                true, "tank", "build", "resources", "cooldowns", Instant.now()));
        data.setPrivacy(player, new PrivacySettings(
                PrivacySettings.Visibility.PARTY, true, true, true, true, false));
        assertEquals("tank", data.readiness(member).get(player).role());

        data.setPrivacy(player, new PrivacySettings(
                PrivacySettings.Visibility.PRIVATE, true, true, true, true, false));

        assertEquals("tank", data.readiness(player).get(player).role());
        assertEquals("hidden", data.readiness(member).get(player).role());
        assertEquals("hidden", data.readiness(member).get(player).build());
    }

    @Test
    void transferJournalPersistsDebitPhase() {
        var data = new MultiplayerSavedData();
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        var offer = data.offerTransfer(sender, recipient, id("currency"), 5);
        data.markTransferDebited(recipient, offer.id());

        MultiplayerSavedData loaded = MultiplayerSavedData.load(
                data.save(new CompoundTag(), null), null);

        assertEquals(MultiplayerSavedData.TransferPhase.DEBITED,
                loaded.pendingTransfer(recipient).phase());
        loaded.completeTransfer(recipient, offer.id());
        assertThrows(IllegalStateException.class, () -> loaded.pendingTransfer(recipient));
    }

    @Test
    void debitedTransferCannotBeReplacedOrCancelledWithoutCompensation() {
        var data = new MultiplayerSavedData();
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        var offer = data.offerTransfer(sender, recipient, id("currency"), 5);
        data.markTransferDebited(recipient, offer.id());

        assertThrows(IllegalStateException.class,
                () -> data.offerTransfer(UUID.randomUUID(), recipient, id("currency"), 2));
        assertThrows(IllegalStateException.class,
                () -> data.cancelTransfer(recipient, offer.id()));
        data.cancelCompensatedTransfer(recipient, offer.id());
        assertThrows(IllegalStateException.class, () -> data.pendingTransfer(recipient));
    }

    @Test
    void unsupportedVersionQuarantinesSocialStore() {
        var root = new CompoundTag();
        root.putInt("data_version", MultiplayerSavedData.DATA_VERSION + 1);

        MultiplayerSavedData loaded = MultiplayerSavedData.load(root, null);

        assertFalse(loaded.active());
        assertTrue(loaded.quarantineReason().isPresent());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
