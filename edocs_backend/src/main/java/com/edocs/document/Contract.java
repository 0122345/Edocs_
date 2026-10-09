package com.edocs.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.edocs.identity.Organization;
import com.edocs.identity.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Contract extends Document and composes 1..* Party.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contracts")
@PrimaryKeyJoinColumn(name = "doc_id")
public class Contract extends Document {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    @Column(columnDefinition = "text")
    private String terms;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "contract_value", precision = 15, scale = 2)
    private BigDecimal value;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("signatureOrder ASC")
    private List<Party> parties = new ArrayList<>();

    public Contract(Organization organization, User owner, String title, String category) {
        super(organization, owner, title, category);
    }

    public void addParty(Party party) {
        party.setContract(this);
        parties.add(party);
    }

    public List<Party> signers() {
        return parties.stream().filter(p -> p.getRole() == PartyRole.SIGNER).toList();
    }

    public boolean allSignersSigned() {
        List<Party> signers = signers();
        return !signers.isEmpty() && signers.stream().allMatch(p -> p.getSignedAt() != null);
    }
}
