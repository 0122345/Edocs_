package com.edocs.seed;

// Contract bodies shared with the frontend mock data (edocs_frontend/src/data/mock.ts).
final class SeedContent {

    private SeedContent() {
    }

    static final String MSA = """
            <h2>1. Executive summary</h2>
            <p>Edocs is an enterprise platform that replaces paper-based contract management with a secure, legally binding digital process. It covers the full e-contract lifecycle: drafting and negotiation, e-signature, automated approval workflows and long-term compliant archiving.</p>
            <h2>2. Problem statement: the paper problem</h2>
            <p>Contract management remains one of the last strongholds of paper. Organizations face linked problems across operational efficiency, cost and regulatory friction.</p>
            <ul>
            <li><strong>Operational inefficiency:</strong> printing, scanning, couriering and manual filing add 3–5 days to average turnaround.</li>
            <li><strong>Cost burden:</strong> organizations spend $20–$50 per paper contract on printing, shipping, storage and labor.</li>
            <li><strong>Risk of loss and tampering:</strong> physical documents are exposed to fire, flooding, misplacement and unauthorized alteration.</li>
            </ul>
            <h2>3. Solution overview and key features</h2>
            <p>Edocs is a modular, API-first platform that can be deployed as cloud SaaS, private cloud or on-premises. <del>The platform mandates strict single-tenant architecture without multi-region failover.</del> <ins>Multi-region active-active high availability with instant cryptographic verification is supported on every enterprise tier.</ins></p>
            <h2>4. Security and compliance framework</h2>
            <p>Regulatory compliance includes the eIDAS Regulation (EU), supporting simple, advanced and qualified electronic signatures. The ESIGN Act and UETA (USA) guarantee intent to sign, consent to electronic records and association of the signature with the record.</p>
            """.strip();

    static final String NDA = """
            <h2>1. Purpose</h2>
            <p>The parties wish to exchange confidential information to evaluate a potential business relationship (the “Purpose”).</p>
            <h2>2. Confidential information</h2>
            <p>“Confidential Information” means any non-public information disclosed by either party, in any form, that is marked confidential or would reasonably be understood to be confidential.</p>
            <h2>3. Obligations</h2>
            <p>The receiving party shall use Confidential Information only for the Purpose and protect it with at least reasonable care.</p>
            <h2>4. Term</h2>
            <p>This Agreement lasts {{term_years}} years from the Effective Date.</p>
            """.strip();

    static final String DPA = """
            <h2>1. Subject matter</h2>
            <p>This addendum governs the processing of personal data by {{processor_name}} on behalf of {{controller_name}}.</p>
            <h2>2. Data residency</h2>
            <p>All personal data is stored in the EU (Frankfurt) region unless the controller agrees otherwise in writing.</p>
            <h2>3. Sub-processors</h2>
            <p>The processor shall not engage sub-processors without prior written authorization from the controller.</p>
            """.strip();

    static final String SLA = """
            <h2>1. Service availability</h2>
            <p>The Provider guarantees {{uptime}} monthly uptime, measured at the API gateway.</p>
            <h2>2. Service credits</h2>
            <p>If availability drops below the guaranteed level, the Client receives service credits as set out in Schedule C.</p>
            """.strip();

    static final String SUPPLY = "<h2>1. Supply</h2><p>The Supplier shall deliver goods according to purchase orders issued under these terms.</p>";
}
