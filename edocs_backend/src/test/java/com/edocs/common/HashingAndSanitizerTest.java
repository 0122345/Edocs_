package com.edocs.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HashingAndSanitizerTest {

    @Test
    void sha256IsStableAndFieldSeparated() {
        assertThat(Hashing.sha256Hex("a", "bc")).hasSize(64).isEqualTo(Hashing.sha256Hex("a", "bc"));
        assertThat(Hashing.sha256Hex("a", "bc")).isNotEqualTo(Hashing.sha256Hex("ab", "c"));
    }

    @Test
    void merkleRootChangesWhenAnyLeafChanges() {
        String root = Hashing.merkleRoot(List.of("a", "b", "c"));
        assertThat(Hashing.merkleRoot(List.of("a", "b", "c"))).isEqualTo(root);
        assertThat(Hashing.merkleRoot(List.of("a", "x", "c"))).isNotEqualTo(root);
        assertThat(Hashing.merkleRoot(List.of("a", "c", "b"))).isNotEqualTo(root);
    }

    @Test
    void shortTxMatchesUiFormat() {
        assertThat(Hashing.shortTx("0x7f8c00000000003b9a")).isEqualTo("0x7f8c…3b9a");
        assertThat(Hashing.shortTx("abcd1234ef")).isEqualTo("0xabcd…34ef");
    }

    @Test
    void sanitizerStripsScriptsButKeepsRedlines() {
        String dirty = "<h2>1. Terms</h2><p onclick=\"steal()\">Hi<script>alert(1)</script> <del>old</del> <ins>new</ins></p><img src=x onerror=alert(1)>";
        String clean = HtmlSanitizer.clean(dirty);
        assertThat(clean).contains("<h2>1. Terms</h2>", "<del>old</del>", "<ins>new</ins>");
        assertThat(clean).doesNotContain("script", "onclick", "onerror", "<img");
    }
}
