package com.eurobuddha.keyreuses;

import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.pqc.crypto.gmss.util.WinternitzOTSignature;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Derives a Minima default key's PUBLIC key from the node's base seed — the exact recipe of
 * minima-core's {@code TreeKey}/{@code TreeKeyNode}/{@code MMR} and Pandamonium's
 * {@code NodeAuditKeys.derivePublicKey}, reduced to the hashes it actually needs (no MMR proofs,
 * no child trees, no signing). Verified against the shared golden vectors in KeyDeriveTest.
 *
 * <p>Read-only: nothing here can sign, and the seed is only ever an input.
 *
 * <p>Serialisation mirrors the Streamable forms the node hashes:
 * MiniData = int32 length + bytes; MiniNumber(n) = scale byte 0, length byte, two's-complement
 * bytes; the key index modifier is {@code BigInteger.toByteArray()} minus a leading zero byte.
 */
final class KeyDerive {

    private KeyDerive() {}

    static final int CLASSIC_SIZE = 64, BLOCK_SIZE = 128;
    static final int CLASSIC_COUNT = 64, BLOCK_COUNT = 32, TOTAL = CLASSIC_COUNT + BLOCK_COUNT;

    private static final int WINTERNITZ = 8;

    static byte[] sha3(byte[]... parts) {
        final SHA3Digest d = new SHA3Digest(256);
        for (byte[] p : parts) d.update(p, 0, p.length);
        final byte[] out = new byte[32];
        d.doFinal(out, 0);
        return out;
    }

    static byte[] data(byte[] b) {
        return ByteBuffer.allocate(4 + b.length).putInt(b.length).put(b).array();
    }

    static byte[] number(long n) {
        final byte[] u = BigInteger.valueOf(n).toByteArray();
        final byte[] out = new byte[2 + u.length];
        out[1] = (byte) u.length;
        System.arraycopy(u, 0, out, 2, u.length);
        return out;
    }

    static byte[] modifier(int index) {
        final byte[] s = BigInteger.valueOf(index).toByteArray();
        return s.length > 1 && s[0] == 0 ? Arrays.copyOfRange(s, 1, s.length) : s;
    }

    /** The tree public key for key {@code index} of a {@code size}-ary default tree (64 or 128). */
    static byte[] publicKey(byte[] baseSeed, int index, int size) {
        if (index < 0 || (size & (size - 1)) != 0 || size < 2) throw new IllegalArgumentException("bad key shape");
        // WalletCore.derivePrivateSeed: hashObjects(baseSeed, MiniData(index))
        final byte[] priv = sha3(data(baseSeed), data(modifier(index)));
        final byte[][] nodes = new byte[size][];
        for (int i = 0; i < size; i++) {
            // TreeKeyNode: WOTS seed = hashAllObjects(MiniNumber(i), privateSeed)
            final byte[] wotsSeed = sha3(number(i), data(priv));
            final byte[] wotsPub = new WinternitzOTSignature(wotsSeed, new SHA3Digest(256), WINTERNITZ).getPublicKey();
            // MMRData.CreateMMRDataLeafNode: hash(ZERO, pubkey, sum ZERO)
            nodes[i] = sha3(number(0), data(wotsPub), number(0));
        }
        // A power-of-two MMR is one perfect binary tree: root = ordered pairwise parents,
        // MMRData.CreateMMRDataParentNode: hash(ONE, left, right, sum ZERO).
        for (int n = size; n > 1; n /= 2) {
            for (int i = 0; i < n / 2; i++) nodes[i] = sha3(number(1), data(nodes[2 * i]), data(nodes[2 * i + 1]), number(0));
        }
        return nodes[0];
    }

    static String hex(byte[] b) {
        final StringBuilder sb = new StringBuilder("0x");
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        final String h = s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
        final byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    /**
     * All 96 default public keys of a seed: classic 64/3 indices 0..63 then block-as-keyuses
     * 128/4 indices 0..31, in the row shape AuditSelection expects (no counter: uses = -1).
     * Derivation runs across cores; {@code progress} is called with the running count.
     */
    static JSONArray defaultKeys(byte[] baseSeed, IntConsumer progress) throws Exception {
        final String[] pks = new String[TOTAL];
        final AtomicInteger done = new AtomicInteger();
        IntStream.range(0, TOTAL).parallel().forEach(i -> {
            final boolean classic = i < CLASSIC_COUNT;
            pks[i] = hex(publicKey(baseSeed, classic ? i : i - CLASSIC_COUNT, classic ? CLASSIC_SIZE : BLOCK_SIZE));
            if (progress != null) progress.accept(done.incrementAndGet());
        });
        final JSONArray out = new JSONArray();
        for (int i = 0; i < TOTAL; i++) {
            final boolean classic = i < CLASSIC_COUNT;
            out.put(new JSONObject().put("publickey", pks[i])
                    .put("size", classic ? CLASSIC_SIZE : BLOCK_SIZE).put("depth", classic ? 3 : 4)
                    .put("maxuses", classic ? 262144 : 268435456).put("uses", -1)
                    .put("index", classic ? i : i - CLASSIC_COUNT));
        }
        return out;
    }
}
