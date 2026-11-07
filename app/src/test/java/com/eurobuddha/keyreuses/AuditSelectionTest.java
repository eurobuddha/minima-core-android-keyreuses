package com.eurobuddha.keyreuses;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

import static org.junit.Assert.*;

/** Standalone port of Pandamonium's AuditSelectionTest (no minimaWallet set). */
public class AuditSelectionTest {
    static String pk(int n) { return String.format(Locale.ROOT, "0x%064X", n + 1); }
    static JSONObject row(int n, int size, int uses) throws Exception {
        return new JSONObject().put("publickey", pk(n)).put("size", size).put("depth", size == 64 ? 3 : 4)
                .put("maxuses", size == 64 ? 262144 : 268435456).put("uses", uses);
    }
    static String derived() throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < 96; i++) a.put(row(i, i < 64 ? 64 : 128, -1).put("index", i < 64 ? i : i - 64));
        return a.toString();
    }
    /** A block-as-keyuses node: holds only the 32 block keys. */
    static String node() throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 64; i < 96; i++) a.put(row(i, 128, 2370000));
        return a.toString();
    }

    @Test public void blockNodeAlwaysChecks32BlockAnd64ClassicAddresses() throws Exception {
        AuditSelection a = new AuditSelection(node(), derived());
        assertEquals(96, a.keys.size());
        for (int i = 0; i < 96; i++) {
            assertEquals(i < 64 ? KeyAudit.Family.CLASSIC : KeyAudit.Family.BLOCK, a.keys.get(i).family);
            assertEquals(i < 64 ? -1 : 2370000, a.keys.get(i).uses);
            assertEquals(i < 64 ? AuditSelection.CLASSIC : AuditSelection.BLOCK | AuditSelection.NODE, a.origins.get(i).intValue());
            assertEquals(a.addresses.get(i).hex, a.hexAddresses.get(i));
        }
        assertEquals(96, new HashSet<>(a.publicKeys).size());
    }

    @Test public void classicNodeFillsCountersAndKeepsBothSourceLabels() throws Exception {
        JSONArray nodes = new JSONArray();
        for (int i = 0; i < 64; i++) nodes.put(row(i, 64, 50 + i));
        AuditSelection a = new AuditSelection(nodes.toString(), derived());
        assertEquals(96, a.keys.size());
        assertEquals(50, a.keys.get(0).uses);
        assertEquals(AuditSelection.CLASSIC | AuditSelection.NODE, a.origins.get(0).intValue());
        assertEquals(-1, a.keys.get(64).uses);
    }

    @Test public void extraNodeKeysAppendAfterTheDerivedSet() throws Exception {
        JSONArray nodes = new JSONArray(node()).put(row(500, 64, 7));
        AuditSelection a = new AuditSelection(nodes.toString(), derived());
        assertEquals(97, a.keys.size());
        assertEquals(AuditSelection.NODE, a.origins.get(96).intValue());
        assertEquals(7, a.keys.get(96).uses);
    }

    @Test public void derivedKeysWithoutCounterStayUnknownUntilPublicEvidence() throws Exception {
        AuditSelection a = new AuditSelection(node(), derived());
        KeyAudit.Result result = KeyAudit.join(a.keys, Collections.emptyList(), Collections.emptyList(), a.addresses);
        assertFalse(result.complete);
        assertFalse(result.publicComplete);
        assertFalse(result.rows.get(0).publicChecked);
    }

    @Test public void malformedIncompleteDuplicateConflictingAndUnreadableSetsFail() throws Exception {
        JSONArray a = new JSONArray(derived()); a.remove(95);
        assertThrows(IllegalStateException.class, () -> new AuditSelection(node(), a.toString()));
        JSONArray duplicate = new JSONArray(node()); duplicate.put(duplicate.get(0));
        assertThrows(IllegalStateException.class, () -> new AuditSelection(duplicate.toString(), derived()));
        JSONArray bad = new JSONArray(node()); bad.put(row(0, 128, 10));
        assertThrows(IllegalStateException.class, () -> new AuditSelection(bad.toString(), derived()));
        JSONArray unreadable = new JSONArray(node()); unreadable.put(row(0, 64, -1));
        assertThrows(IllegalStateException.class, () -> new AuditSelection(unreadable.toString(), derived()));
        assertThrows(IllegalStateException.class, () -> new AuditSelection("[]", derived()));
        assertThrows(IllegalStateException.class, () -> new AuditSelection("not json", derived()));
    }
}
