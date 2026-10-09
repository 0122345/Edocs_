package com.edocs.common;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

// Server-side allow-list for editor HTML so stored content can never carry scripts.
public final class HtmlSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.LINKS)
            .and(new HtmlPolicyBuilder().allowElements("del", "ins", "mark", "hr").allowAttributes("data-variable").onElements("span").toFactory());

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        return html == null ? "" : POLICY.sanitize(html);
    }
}
