package com.edocs.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArchiveSearchTermsTest {

    @Test
    void splitsOnWhitespaceAndDropsAnd() {
        assertThat(ArchiveService.terms("MSA and Indemnity")).containsExactly("msa", "indemnity");
    }

    @Test
    void fieldPrefixesKeepOnlyTheValue() {
        assertThat(ArchiveService.terms("status:signed title:nda")).containsExactly("signed", "nda");
    }

    @Test
    void blankQueryMeansNoFilter() {
        assertThat(ArchiveService.terms("  ")).isEmpty();
        assertThat(ArchiveService.terms(null)).isEmpty();
    }
}
