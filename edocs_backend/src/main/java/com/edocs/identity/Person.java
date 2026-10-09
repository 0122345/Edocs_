package com.edocs.identity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

// «abstract» Person from the class diagram: shared identity attributes.
@Getter
@Setter
@MappedSuperclass
public abstract class Person {

    @Column(name = "full_name", nullable = false, length = 120)
    protected String fullName;

    @Column(nullable = false, unique = true, length = 254)
    protected String email;

    @Column(length = 32)
    protected String phone;

    public String initials() {
        String[] parts = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.isEmpty() ? "?" : sb.toString();
    }
}
