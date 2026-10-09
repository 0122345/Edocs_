package com.edocs.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.edocs.audit.AuditService.Actor;

class AuditServiceTest {

    private final UUID org = UUID.randomUUID();
    private final List<AuditLog> store = new ArrayList<>();
    private AuditLogRepository repo;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repo = mock(AuditLogRepository.class);
        when(repo.findTopByOrgIdOrderBySeqDesc(anyString()))
                .thenAnswer(i -> store.stream().max(Comparator.comparingLong(AuditLog::getSeq)));
        when(repo.insert(any(AuditLog.class))).thenAnswer(i -> {
            AuditLog e = i.getArgument(0);
            store.add(e);
            return e;
        });
        when(repo.findByOrgIdOrderBySeqAsc(anyString()))
                .thenAnswer(i -> store.stream().sorted(Comparator.comparingLong(AuditLog::getSeq)));
        service = new AuditService(repo);
    }

    private AuditLog append(String event) {
        return service.append(Actor.system(org, "tester", "127.0.0.1"), AuditKind.VIEW, event, null, "Workspace", AuditStatus.VERIFIED, Instant.now());
    }

    @Test
    void entriesFormAHashChain() {
        AuditLog first = append("one");
        AuditLog second = append("two");
        assertThat(first.getPrevHash()).isEqualTo(AuditService.GENESIS);
        assertThat(second.getPrevHash()).isEqualTo(first.getHash());
        assertThat(second.getSeq()).isEqualTo(2);
        assertThat(service.verify(org).intact()).isTrue();
    }

    @Test
    void editingAnEntryBreaksTheChain() {
        append("one");
        append("two");
        append("three");
        AuditLog original = store.get(1);
        store.set(1, original.toBuilder().event("tampered").build());
        AuditService.ChainReport report = service.verify(org);
        assertThat(report.intact()).isFalse();
        assertThat(report.brokenAtSeq()).isEqualTo(2L);
    }

    @Test
    void deletingAnEntryBreaksTheChain() {
        append("one");
        append("two");
        append("three");
        store.remove(1);
        assertThat(service.verify(org).intact()).isFalse();
    }

    @Test
    void retriesWhenAnotherWriterTakesTheSequenceNumber() {
        append("one");
        when(repo.insert(any(AuditLog.class)))
                .thenThrow(new DuplicateKeyException("race"))
                .thenAnswer(i -> {
                    AuditLog e = i.getArgument(0);
                    store.add(e);
                    return e;
                });
        AuditLog entry = append("two");
        assertThat(entry.getSeq()).isEqualTo(2);
        assertThat(store).hasSize(2);
    }

    @Test
    void emptyChainIsIntact() {
        when(repo.findTopByOrgIdOrderBySeqDesc(anyString())).thenReturn(Optional.empty());
        assertThat(service.verify(org)).isEqualTo(new AuditService.ChainReport(0, true, null));
    }
}
