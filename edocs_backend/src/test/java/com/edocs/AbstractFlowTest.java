package com.edocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.jayway.jsonpath.JsonPath;

// End-to-end user journeys through the real HTTP + security stack; subclasses choose the infrastructure.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "edocs.security.jwt-secret=flow-test-secret-flow-test-secret-flow-test-0123456789",
        "edocs.crypto.master-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "edocs.seed.enabled=true",
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

    // Codes and temporary passwords only travel inside emails, so the journeys read them from the outgoing wire body.
    @MockitoSpyBean
    NotificationService notifications;

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

    @Test
    @Order(10)
    void selfServiceSignupCreatesAnIsolatedWorkspaceWithRoles() throws Exception {
        String owner = "founder" + System.nanoTime() + "@newco.example";
        String password = "Launch2026rocket";

        // Weak passwords are refused; a good one creates the workspace but no session until the email is confirmed.
        mvc.perform(publicPost("/auth/signup", signup("NewCo", owner, "short1"))).andExpect(status().isUnprocessableEntity());
        String created = mvc.perform(publicPost("/auth/signup", signup("NewCo", owner, password)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("verify")).andExpect(jsonPath("$.session").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String firstChallenge = JsonPath.read(created, "$.challengeId");
        mvc.perform(publicPost("/auth/mfa/verify", "{\"challengeId\":\"" + firstChallenge + "\",\"code\":\"000000\"}")).andExpect(status().is4xxClientError());
        String retry = mvc.perform(publicPost("/auth/login", credentials(owner, password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("verify")).andReturn().getResponse().getContentAsString();
        String code = code(lastWire(owner));
        String session = mvc.perform(publicPost("/auth/mfa/verify", "{\"challengeId\":\"" + JsonPath.read(retry, "$.challengeId") + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("admin")).andReturn().getResponse().getContentAsString();
        String admin = JsonPath.read(session, "$.token");

        // The new workspace starts provisioned and sees nothing of the seeded one.
        mvc.perform(as(admin, get("/documents"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(admin, get("/templates"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(as(admin, get("/compliance/controls"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(as(admin, get("/members"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(admin, get("/settings"))).andExpect(status().isOk()).andExpect(jsonPath("$.orgName").value("NewCo"));
        String acmeDoc = docIdByTitle(login("s.jenkins@acme.corp"), "Enterprise SLA v2");
        mvc.perform(as(admin, get("/documents/" + acmeDoc))).andExpect(status().isNotFound());

        // Members of another workspace can't be pulled in; new people get a role and a temporary password by email.
        mvc.perform(as(admin, post("/members")).content("{\"email\":\"s.jenkins@acme.corp\",\"name\":\"Sarah\",\"role\":\"legal\"}"))
                .andExpect(status().isConflict());
        String member = "signer" + System.nanoTime() + "@newco.example";
        mvc.perform(as(admin, post("/members")).content("{\"email\":\"" + member + "\",\"name\":\"Sam Signer\",\"role\":\"signer\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("signer"));
        Matcher temp = Pattern.compile("Temporary password: (\\S+) ").matcher(lastWire(member));
        assertThat(temp.find()).isTrue();
        String signer = JsonPath.read(mvc.perform(publicPost("/auth/login", credentials(member, temp.group(1))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.session.token");
        mvc.perform(as(signer, post("/members")).content("{\"email\":\"x@newco.example\",\"role\":\"legal\"}")).andExpect(status().isForbidden());

        // The temporary password is replaced in settings, and a forgotten one is reset with an emailed code.
        mvc.perform(as(signer, post("/auth/password")).content("{\"currentPassword\":\"wrong-one-123\",\"newPassword\":\"Signer2026safe\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(as(signer, post("/auth/password")).content("{\"currentPassword\":\"" + temp.group(1) + "\",\"newPassword\":\"Signer2026safe\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(publicPost("/auth/password/forgot", "{\"email\":\"" + member + "\"}")).andExpect(status().isAccepted());
        mvc.perform(publicPost("/auth/password/forgot", "{\"email\":\"nobody" + System.nanoTime() + "@newco.example\"}")).andExpect(status().isAccepted());
        mvc.perform(publicPost("/auth/password/reset", "{\"email\":\"" + member + "\",\"code\":\"" + code(lastWire(member)) + "\",\"newPassword\":\"Recovered2026now\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(publicPost("/auth/login", credentials(member, "Signer2026safe"))).andExpect(status().isUnauthorized());
        mvc.perform(publicPost("/auth/login", credentials(member, "Recovered2026now"))).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("session"));

        // A confirmed email can't be signed up twice; an unconfirmed sign-up is simply replaced.
        mvc.perform(publicPost("/auth/signup", signup("Copycat", owner, password))).andExpect(status().isConflict());
        String pending = "pending" + System.nanoTime() + "@newco.example";
        mvc.perform(publicPost("/auth/signup", signup("First try", pending, password))).andExpect(status().isCreated());
        mvc.perform(publicPost("/auth/signup", signup("Second try", pending, password))).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, pending)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from organizations where admin_email = ?", Integer.class, pending)).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder publicPost(String path, String body) {
        // A separate client address keeps these calls out of the per-IP sign-in budget the other journeys share.
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body).with(r -> {
            r.setRemoteAddr("10.20.30.40");
            return r;
        });
    }

    private static String signup(String org, String email, String password) {
        return "{\"orgName\":\"" + org + "\",\"name\":\"Nia Founder\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String code(String wire) {
        Matcher m = Pattern.compile("(\\d{6})").matcher(wire);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private String lastWire(String email) {
        ArgumentCaptor<Recipient> to = ArgumentCaptor.forClass(Recipient.class);
        ArgumentCaptor<String> wire = ArgumentCaptor.forClass(String.class);
        verify(notifications, atLeastOnce()).send(any(), to.capture(), any(), any(), any(), any(), any(), wire.capture());
        for (int i = to.getAllValues().size() - 1; i >= 0; i--) {
            if (email.equals(to.getAllValues().get(i).email())) {
                return wire.getAllValues().get(i);
            }
        }
        throw new AssertionError("No email was sent to " + email);
    }
}
