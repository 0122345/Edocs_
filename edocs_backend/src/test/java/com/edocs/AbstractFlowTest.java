package com.edocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

// End-to-end user journeys through the real HTTP + security stack; subclasses choose the infrastructure.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "edocs.security.demo-mfa-code=246810",
        "edocs.otp.echo-in-app=true",
        "spring.mail.host=localhost",
        "spring.mail.port=3025"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractFlowTest {

    private static final String PASSWORD = "Demo@2026";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.session.token");
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String json(String token, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(as(token, req)).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private String docIdByTitle(String token, String title) throws Exception {
        List<String> ids = JsonPath.read(json(token, get("/documents")), "$[?(@.title == '" + title + "')].id");
        assertThat(ids).hasSize(1);
        return ids.getFirst();
    }

    @Test
    @Order(1)
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/documents")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session has ended. Sign in again."));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void wrongPasswordIsRejectedWithoutRevealingTheAccount() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@acme.corp\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Email or password is incorrect."));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"s.jenkins@acme.corp\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    @Order(3)
    void adminSignInNeedsMfa() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"j.davis@acme.corp\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("mfa")).andReturn().getResponse().getContentAsString();
        String challenge = JsonPath.read(body, "$.challengeId");
        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON).content("{\"challengeId\":\"" + challenge + "\",\"code\":\"000000\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON).content("{\"challengeId\":\"" + challenge + "\",\"code\":\"246810\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("admin")).andExpect(jsonPath("$.token").isString());
    }

    @Test
    @Order(4)
    void rbacIsEnforcedPerRole() throws Exception {
        String auditor = login("e.rostova@acme.corp");
        String signer = login("m.vance@partnercorp.io");
        String create = "{\"title\":\"Should fail\",\"category\":\"Commercial\",\"parties\":[]}";
        mvc.perform(as(auditor, post("/documents")).content(create)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role does not allow this action."));
        mvc.perform(as(signer, post("/documents")).content(create)).andExpect(status().isForbidden());
        mvc.perform(as(signer, put("/settings")).content("{\"orgName\":\"Hijack\",\"adminEmail\":\"x@y.z\",\"signatureLevel\":\"ses\",\"retentionYears\":1,\"require2fa\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(auditor, get("/archive"))).andExpect(status().isOk());
        mvc.perform(as(signer, get("/archive"))).andExpect(status().isForbidden());
        // A signer only sees documents they are a party to.
        List<String> titles = JsonPath.read(json(signer, get("/documents")), "$[*].title");
        assertThat(titles).containsExactly("Q3 Master Services Agreement");
    }

    @Test
    @Order(5)
    void signerCannotReadOtherDocuments() throws Exception {
        String legal = login("s.jenkins@acme.corp");
        String signer = login("m.vance@partnercorp.io");
        String dpa = docIdByTitle(legal, "Data Processing Addendum – Initech");
        mvc.perform(as(signer, get("/documents/" + dpa))).andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void documentLifecycleFromDraftToSealed() throws Exception {
        String legal = login("s.jenkins@acme.corp");
        List<String> templateIds = JsonPath.read(json(legal, get("/templates")), "$[?(@.name == 'Mutual NDA')].id");
        String templateId = templateIds.getFirst();
        String created = mvc.perform(as(legal, post("/documents")).content("""
                {"title":"Integration NDA","category":"Confidentiality","templateId":"%s",
                 "parties":[{"name":"Marcus Vance","email":"m.vance@partnercorp.io","role":"signer","order":1}],
                 "value":1000,"effectiveDate":"2026-11-01","expiryDate":"2027-11-01"}""".formatted(templateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.workflowStep").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        // Saving strips scripts and bumps the version.
        mvc.perform(as(legal, patch("/documents/" + id)).content("{\"content\":\"<p>Clause<script>alert(1)</script></p>\",\"note\":\"Tightened\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.content").value("<p>Clause</p>"))
                .andExpect(jsonPath("$.versions[0].note").value("Tightened"));

        mvc.perform(as(legal, post("/documents/" + id + "/comments")).content("{\"body\":\"Check the term.\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value("Sarah Jenkins"));

        mvc.perform(as(legal, post("/documents/" + id + "/send"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("out_for_signature"))
                .andExpect(jsonPath("$.workflowStep").value(3));

        // Signed-in signer requests a code, reads it from the in-app echo, and signs.
        String signer = login("m.vance@partnercorp.io");
        mvc.perform(as(signer, post("/documents/" + id + "/signatures")).content("{\"mode\":\"type\",\"otp\":\"123456\",\"signature\":\"Marcus Vance\"}"))
                .andExpect(status().isUnprocessableEntity());
        // A phone on file must not swallow the code while only the logging SMS stand-in is wired: it goes by email.
        jdbc.update("UPDATE users SET phone = '+250788123456' WHERE email = 'm.vance@partnercorp.io'");
        mvc.perform(as(signer, post("/documents/" + id + "/otp"))).andExpect(status().isOk()).andExpect(jsonPath("$.sentTo").value("m•••e@partnercorp.io"));
        String notes = json(signer, get("/notifications"));
        Matcher m = Pattern.compile("signing code is (\\d{6})").matcher(notes);
        assertThat(m.find()).isTrue();
        mvc.perform(as(signer, post("/documents/" + id + "/signatures"))
                        .content("{\"mode\":\"type\",\"otp\":\"" + m.group(1) + "\",\"signature\":\"Marcus Vance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.status").value("signed"))
                .andExpect(jsonPath("$.document.workflowStep").value(5))
                .andExpect(jsonPath("$.document.sha256").isString())
                .andExpect(jsonPath("$.txId").isString());

        // Sealed documents cannot change.
        mvc.perform(as(legal, patch("/documents/" + id)).content("{\"content\":\"<p>edit</p>\"}")).andExpect(status().isConflict());
        String admin = mfaLogin();
        mvc.perform(as(admin, delete("/documents/" + id))).andExpect(status().isConflict());

        // The anchor event travels through RabbitMQ and lands in the audit trail.
        String auditor = login("e.rostova@acme.corp");
        boolean anchored = false;
        for (int i = 0; i < 40 && !anchored; i++) {
            List<String> events = JsonPath.read(json(auditor, get("/archive/" + id)), "$.timeline[*].title");
            anchored = events.contains("Merkle root anchored");
            if (!anchored) {
                Thread.sleep(250);
            }
        }
        assertThat(anchored).as("anchor event consumed from RabbitMQ").isTrue();

        mvc.perform(as(auditor, post("/audit-logs/verify"))).andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(true));
        mvc.perform(as(auditor, get("/documents/" + id + "/evidence"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].signer").value("m.vance@partnercorp.io"))
                .andExpect(jsonPath("$.auditChainIntact").value(true));
    }

    @Test
    @Order(7)
    void logoutRevokesTheToken() throws Exception {
        String legal = login("s.jenkins@acme.corp");
        mvc.perform(as(legal, get("/auth/me"))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("s.jenkins@acme.corp"));
        mvc.perform(as(legal, post("/auth/logout"))).andExpect(status().isNoContent());
        mvc.perform(as(legal, get("/auth/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void deactivatedMembersLoseAccessImmediately() throws Exception {
        String admin = mfaLogin();
        String auditor = login("e.rostova@acme.corp");
        List<String> ids = JsonPath.read(json(admin, get("/members")), "$[?(@.email == 'e.rostova@acme.corp')].id");
        mvc.perform(as(admin, patch("/members/" + ids.getFirst())).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(as(auditor, get("/documents"))).andExpect(status().isUnauthorized());
        mvc.perform(as(admin, patch("/members/" + ids.getFirst())).content("{\"active\":true}")).andExpect(status().isOk());
    }

    @Test
    @Order(9)
    void dashboardAndComplianceReflectLiveData() throws Exception {
        String admin = mfaLogin();
        mvc.perform(as(admin, get("/dashboard"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.length()").value(4))
                .andExpect(jsonPath("$.riskBands.monthly.length()").value(3));
        mvc.perform(as(admin, post("/compliance/checks"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Access review for privileged roles')].status").value("pass"));
    }

    private String mfaLogin() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"j.davis@acme.corp\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString();
        String challenge = JsonPath.read(body, "$.challengeId");
        String session = mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challenge + "\",\"code\":\"246810\"}")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(session, "$.token");
    }
}
