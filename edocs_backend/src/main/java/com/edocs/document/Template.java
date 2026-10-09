package com.edocs.document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edocs.identity.Organization;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "templates")
public class Template extends ContentItem {

    @Id
    @Column(name = "template_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "template_variables", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    @Column(name = "variable", nullable = false, length = 64)
    private List<String> variables = new ArrayList<>();

    public Template(Organization organization, String title, String category, String content, List<String> variables) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.title = title;
        this.category = category;
        this.content = content;
        this.variables = new ArrayList<>(variables);
    }
}
