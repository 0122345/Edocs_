package com.edocs.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // Pairwise SHA-256 Merkle root; the last leaf is duplicated on odd levels.
    public static String merkleRoot(List<String> leaves) {
        if (leaves.isEmpty()) {
            return sha256Hex("");
        }
        List<String> level = new ArrayList<>(leaves);
        while (level.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = i + 1 < level.size() ? level.get(i + 1) : left;
                next.add(sha256Hex(left, right));
            }
            level = next;
        }
        return level.getFirst();
    }

    // Display form used by the UI, e.g. 0x7f8c…3b9a.
    public static String shortTx(String hex) {
        if (hex == null || hex.length() < 8) {
            return hex;
        }
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
        return "0x" + clean.substring(0, 4) + "…" + clean.substring(clean.length() - 4);
    }
}
