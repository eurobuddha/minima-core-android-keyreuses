package com.eurobuddha.keyreuses;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** KeyDerive must reproduce the node's own TreeKey public keys byte for byte — checked against the
 *  golden vectors shared with Pandamonium's NodeAuditKeys (keyuses-derive-vectors.json). */
public class KeyDeriveTest {

    private static JSONArray vectors() throws Exception {
        String json = new String(KeyDeriveTest.class.getResourceAsStream("/keyuses-derive-vectors.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        return new JSONObject(json).getJSONArray("vectors");
    }

    @Test public void matchesEveryGoldenVectorInBothShapes() throws Exception {
        JSONArray v = vectors();
        assertTrue(v.length() >= 8);
        int classic = 0, block = 0;
        for (int i = 0; i < v.length(); i++) {
            JSONObject t = v.getJSONObject(i);
            byte[] seed = KeyDerive.unhex(t.getString("baseseed"));
            String pk = KeyDerive.hex(KeyDerive.publicKey(seed, t.getInt("index"), t.getInt("keysPerLevel")));
            assertEquals(t.toString(), t.getString("publickey"), pk);
            MinimaAddress.Result a = MinimaAddress.fromPublicKey(pk);
            assertEquals(t.getString("address"), a.hex);
            assertEquals(t.getString("mx"), a.mx);
            if (t.getInt("keysPerLevel") == 64) classic++; else block++;
        }
        assertTrue(classic > 0 && block > 0);
    }

    @Test public void serialisationMirrorsMiniDataAndMiniNumber() {
        assertArrayEquals(new byte[]{0, 0, 0, 2, 1, 2}, KeyDerive.data(new byte[]{1, 2}));
        assertArrayEquals(new byte[]{0, 1, 0}, KeyDerive.number(0));
        assertArrayEquals(new byte[]{0, 1, 127}, KeyDerive.number(127));
        assertArrayEquals(new byte[]{0, 2, 0, (byte) 0x80}, KeyDerive.number(128));
        assertArrayEquals(new byte[]{0}, KeyDerive.modifier(0));
        assertArrayEquals(new byte[]{63}, KeyDerive.modifier(63));
        assertArrayEquals(new byte[]{(byte) 0x80}, KeyDerive.modifier(128));
    }

    @Test public void defaultSetIs64ClassicThen32BlockAllDistinctWithNoCounter() throws Exception {
        JSONArray v = vectors();
        byte[] seed = KeyDerive.unhex(v.getJSONObject(0).getString("baseseed"));
        int[] progress = {0};
        JSONArray keys = KeyDerive.defaultKeys(seed, done -> progress[0] = Math.max(progress[0], done));
        assertEquals(96, keys.length());
        assertEquals(96, progress[0]);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 96; i++) {
            JSONObject k = keys.getJSONObject(i);
            assertEquals(i < 64 ? 64 : 128, k.getInt("size"));
            assertEquals(i < 64 ? 3 : 4, k.getInt("depth"));
            assertEquals(i < 64 ? i : i - 64, k.getInt("index"));
            assertEquals(-1, k.getInt("uses"));
            assertTrue(seen.add(k.getString("publickey")));
        }
        // the derived set agrees with the vectors for the same seed
        for (int i = 0; i < v.length(); i++) {
            JSONObject t = v.getJSONObject(i);
            if (!t.getString("baseseed").equalsIgnoreCase(v.getJSONObject(0).getString("baseseed"))) continue;
            int at = t.getInt("keysPerLevel") == 64 ? t.getInt("index") : 64 + t.getInt("index");
            assertEquals(t.getString("publickey"), keys.getJSONObject(at).getString("publickey"));
        }
        // AuditSelection accepts exactly this shape
        AuditSelection sel = new AuditSelection(new JSONArray().put(keys.getJSONObject(64).put("uses", 2370000)).toString(), keys.toString());
        assertEquals(96, sel.keys.size());
    }
}
