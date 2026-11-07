package com.eurobuddha.keyreuses;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The audit's key set: the 96 keys derived from the node seed (64 classic + 32 block-as-keyuses)
 * merged with whatever {@code keys action:list} holds. Port of Pandamonium KeyUses 1.9.97's
 * AuditSelection minus its in-process minimaWallet set. Public snapshots only; no counter is
 * ever written. Fails closed on anything malformed, incomplete or conflicting.
 */
final class AuditSelection {
    static final int NODE = 1, CLASSIC = 2, BLOCK = 4;

    final List<KeyAudit.LocalKey> keys = new ArrayList<>();
    final List<MinimaAddress.Result> addresses = new ArrayList<>();
    /** Bit set of NODE/CLASSIC/BLOCK per row, parallel to keys. */
    final List<Integer> origins = new ArrayList<>();
    final List<String> publicKeys = new ArrayList<>(), hexAddresses = new ArrayList<>();

    private static final class Entry {
        final String pk; final int size, depth, max; int uses, origin;
        Entry(JSONObject row, int origin) throws Exception {
            pk = row.getString("publickey"); size = number(row, "size"); depth = number(row, "depth");
            max = number(row, "maxuses"); uses = number(row, "uses"); this.origin = origin;
            if (!pk.matches("0x[0-9a-fA-F]{64}") || uses < -1
                    || !((size == 64 && depth == 3 && max == 262144) || (size == 128 && depth == 4 && max == 268435456)))
                throw new IllegalArgumentException("Invalid public key metadata");
        }
    }

    /** @param node    rows of {@code keys action:list}
     *  @param derived rows of {@link KeyDerive#defaultKeys} */
    AuditSelection(String node, String derived) {
        try {
            final Map<String, Entry> merged = new LinkedHashMap<>();
            // Derivation carries no historical counter; an absent value stays unknown.
            final JSONArray defaults = new JSONArray(derived);
            int classic = 0, block = 0;
            final Set<String> indices = new HashSet<>();
            for (int i = 0; i < defaults.length(); i++) {
                final JSONObject row = defaults.getJSONObject(i);
                final int size = number(row, "size"), index = number(row, "index");
                if (index < 0 || index >= (size == 64 ? KeyDerive.CLASSIC_COUNT : KeyDerive.BLOCK_COUNT)
                        || !indices.add(size + ":" + index))
                    throw new IllegalArgumentException("Incomplete derived key set");
                add(merged, new Entry(row, size == 64 ? CLASSIC : BLOCK), false);
                if (size == 64) classic++; else block++;
            }
            if (classic != KeyDerive.CLASSIC_COUNT || block != KeyDerive.BLOCK_COUNT)
                throw new IllegalArgumentException("Incomplete derived key set");
            final JSONArray nodes = new JSONArray(node);
            if (nodes.length() == 0) throw new IllegalArgumentException("No node keys");
            final Set<String> seen = new HashSet<>();
            for (int i = 0; i < nodes.length(); i++) {
                final Entry next = new Entry(nodes.getJSONObject(i), NODE);
                // A held key always has a counter; an unreadable one fails the audit rather than
                // being guessed (Pandamonium NodeAuditKeys does the same).
                if (next.uses < 0) throw new IllegalArgumentException("Node counter unreadable");
                if (!seen.add(next.pk.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate node key");
                add(merged, next, true);
            }
            for (Entry e : merged.values()) {
                final MinimaAddress.Result address = MinimaAddress.fromPublicKey(e.pk);
                if (address == null) throw new IllegalArgumentException("Invalid public address");
                keys.add(new KeyAudit.LocalKey(e.pk, e.uses, e.max, e.size, e.depth));
                addresses.add(address); origins.add(e.origin);
                publicKeys.add(e.pk); hexAddresses.add(address.hex);
            }
        } catch (Exception invalid) {
            throw new IllegalStateException("Public audit metadata unavailable", invalid);
        }
    }

    private static int number(JSONObject row, String field) throws Exception {
        return new java.math.BigDecimal(row.get(field).toString()).intValueExact();
    }

    private static void add(Map<String, Entry> all, Entry next, boolean merge) {
        final String id = next.pk.toUpperCase(Locale.ROOT);
        final Entry prior = all.get(id);
        if (prior == null) { all.put(id, next); return; }
        if (!merge || prior.size != next.size || prior.depth != next.depth || prior.max != next.max)
            throw new IllegalArgumentException("Conflicting derived keys");
        // A node row fills the derived row's missing counter.
        prior.uses = Math.max(prior.uses, next.uses);
        prior.origin |= next.origin;
    }
}
