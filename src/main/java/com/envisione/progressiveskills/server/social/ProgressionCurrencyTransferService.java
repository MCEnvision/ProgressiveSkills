package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProgressionCurrencyTransferService {
    private static final ResourceLocation ORIGIN = ResourceLocation.fromNamespaceAndPath(
            ProjectIdentity.MOD_ID, "social/currency_transfer");

    private ProgressionCurrencyTransferService() {
    }

    public static MultiplayerSavedData.TransferOffer offer(
            ServerPlayer sender,
            ServerPlayer recipient,
            ResourceLocation currencyId,
            long amount
    ) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        if (sender.getServer() != recipient.getServer()) {
            throw new IllegalArgumentException("Transfer players are not on the same server");
        }
        CurrencyDefinition currency = currency(currencyId);
        requireTransferBalances(sender, recipient, currency, amount);
        return MultiplayerSavedData.get(sender.getServer()).offerTransfer(
                sender.getUUID(), recipient.getUUID(), currency.id(), amount);
    }

    public static MultiplayerSavedData.TransferOffer accept(ServerPlayer recipient) {
        Objects.requireNonNull(recipient, "recipient");
        MultiplayerSavedData data = MultiplayerSavedData.get(recipient.getServer());
        MultiplayerSavedData.TransferOffer offer = data.pendingTransfer(recipient.getUUID());
        ServerPlayer sender = recipient.getServer().getPlayerList().getPlayer(offer.sender());
        if (sender == null) {
            throw new IllegalStateException("Transfer sender must be online");
        }
        CurrencyDefinition currency = currency(offer.currency());
        if (offer.phase() == MultiplayerSavedData.TransferPhase.OFFERED) {
            requireRecipientCapacity(recipient, currency, offer.amount());
            TransactionResult debit = mutate(
                    sender,
                    sender.getUUID(),
                    currency,
                    Math.negateExact(offer.amount()),
                    key(offer.id(), "debit"),
                    ProgressionCause.GAMEPLAY,
                    "Debit progression currency transfer"
            );
            if (!debit.status().committed()) {
                data.cancelTransfer(recipient.getUUID(), offer.id());
                throw new IllegalStateException("Transfer debit failed. " + debit.message());
            }
            offer = data.markTransferDebited(recipient.getUUID(), offer.id());
        }

        TransactionResult credit = mutate(
                recipient,
                offer.sender(),
                currency,
                offer.amount(),
                key(offer.id(), "credit"),
                ProgressionCause.GAMEPLAY,
                "Credit progression currency transfer"
        );
        if (!credit.status().committed()) {
            TransactionResult compensation = mutate(
                    sender,
                    sender.getUUID(),
                    currency,
                    offer.amount(),
                    key(offer.id(), "compensation"),
                    ProgressionCause.RECONCILE,
                    "Compensate progression currency transfer"
            );
            if (compensation.status().committed()) {
                data.cancelCompensatedTransfer(recipient.getUUID(), offer.id());
            }
            throw new IllegalStateException("Transfer credit failed. " + credit.message()
                    + ". Compensation " + compensation.message());
        }
        data.completeTransfer(recipient.getUUID(), offer.id());
        return offer;
    }

    public static long balance(ServerPlayer player, ResourceLocation currencyId) {
        CurrencyDefinition currency = currency(currencyId);
        ProgressionSnapshot snapshot = transactions(player).service().snapshot(player.getUUID());
        return logicalBalance(snapshot, currency);
    }

    public static long grant(ServerPlayer target, ResourceLocation currencyId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Currency grant must not be negative");
        }
        CurrencyDefinition currency = currency(currencyId);
        TransactionResult result = mutate(
                target,
                target.getUUID(),
                currency,
                amount,
                new IdempotencyKey("phase18.transfer.admin." + UUID.randomUUID()),
                ProgressionCause.ADMIN,
                "Grant progression currency"
        );
        requireCommitted(result, "Currency grant");
        return balance(target, currency.id());
    }

    private static void requireTransferBalances(
            ServerPlayer sender,
            ServerPlayer recipient,
            CurrencyDefinition currency,
            long amount
    ) {
        if (amount < 1) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        var context = transactions(sender);
        long senderBalance = logicalBalance(context.service().snapshot(sender.getUUID()), currency);
        if (Math.subtractExact(senderBalance, amount) < currency.minimum()) {
            throw new IllegalStateException("Transfer sender balance is too low");
        }
        requireRecipientCapacity(recipient, currency, amount);
    }

    private static void requireRecipientCapacity(
            ServerPlayer recipient,
            CurrencyDefinition currency,
            long amount
    ) {
        var context = transactions(recipient);
        long recipientBalance = logicalBalance(context.service().snapshot(recipient.getUUID()), currency);
        long recipientAfter = Math.addExact(recipientBalance, amount);
        if (recipientAfter > currency.maximum()) {
            throw new IllegalStateException("Transfer recipient balance would exceed its maximum");
        }
    }

    private static TransactionResult mutate(
            ServerPlayer target,
            UUID actor,
            CurrencyDefinition currency,
            long logicalDelta,
            IdempotencyKey key,
            ProgressionCause cause,
            String reason
    ) {
        var context = transactions(target);
        var revision = TransactionRuntime.currentDefinition().orElseThrow(
                () -> new IllegalStateException("Live definitions are unavailable"));
        ProgressionSnapshot snapshot = context.service().snapshot(target.getUUID());
        long logicalBefore = logicalBalance(snapshot, currency);
        long storedBefore = snapshot.balances().getOrDefault(currency.id(), 0L);
        long logicalAfter = Math.addExact(logicalBefore, logicalDelta);
        long storedDelta = Math.subtractExact(logicalAfter, storedBefore);
        TransactionStep step = new TransactionStep(
                ORIGIN,
                List.of(new BalanceMutation(
                        currency.id(), storedDelta, currency.minimum(), currency.maximum())),
                List.of(),
                List.of(),
                List.of()
        );
        TransactionPlan plan = new TransactionPlan(
                actor,
                target.getUUID(),
                key,
                snapshot.stateRevision(),
                revision,
                cause,
                reason,
                step
        );
        return context.executeAndPersist(target, CascadePlan.single(plan), revision);
    }

    private static long logicalBalance(ProgressionSnapshot snapshot, CurrencyDefinition currency) {
        return snapshot.balances().getOrDefault(currency.id(), currency.initial());
    }

    private static CurrencyDefinition currency(ResourceLocation id) {
        return PackRuntime.service().map(service -> SkillCatalog.from(
                        service.live().snapshot().canonicalIr()).currency(id).orElseThrow(
                        () -> new IllegalArgumentException("Unknown progression currency " + id)))
                .orElseThrow(() -> new IllegalStateException("Live definitions are unavailable"));
    }

    private static TransactionRuntime.Context transactions(ServerPlayer player) {
        return TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
    }

    private static IdempotencyKey key(UUID offerId, String phase) {
        return new IdempotencyKey("phase18.transfer." + offerId + "." + phase);
    }

    private static void requireCommitted(TransactionResult result, String operation) {
        if (!result.status().committed()) {
            throw new IllegalStateException(operation + " failed. " + result.message());
        }
    }
}
