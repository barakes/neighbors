package io.github.barakes.neighbors.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.barakes.neighbors.TestcontainersConfiguration;
import io.github.barakes.neighbors.account.AccountRepository;
import io.github.barakes.neighbors.account.LoginTokenRepository;
import io.github.barakes.neighbors.ladder.LadderStepRepository;

/**
 * The API as the app sees it: real HTTP requests through the controllers, the security headers filter
 * and a real, temporary PostgreSQL. Checks the status codes, not only the services behind them.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HttpApiTest {

    /** Every test comes from its own address, so the attempt limits of one test don't touch another. */
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern FIRST_ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SecurityHeadersFilter securityHeaders;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @Autowired
    private LadderStepRepository steps;

    private MockMvc mvc;
    private String address;

    @BeforeEach
    void setUp() {
        steps.deleteAll();
        tokens.deleteAll();
        accounts.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityHeaders).build();
        address = "198.51.100." + NEXT_ADDRESS.getAndIncrement();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body).header("CF-Connecting-IP", address);
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Not found in: " + body);
        }
        return matcher.group(1);
    }

    private String signUp(String username) throws Exception {
        String body = mvc.perform(json(post("/api/account/register"), credentials(username, "a-good-password")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return find(TOKEN, body);
    }

    private String firstStepId(String token) throws Exception {
        String body = mvc.perform(get("/api/ladder").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return find(FIRST_ID, body);
    }

    @Test
    void signUpAndUseTheLadder() throws Exception {
        String token = signUp("Dana");
        String stepId = firstStepId(token);
        mvc.perform(post("/api/ladder/" + stepId + "/reached").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/ladder"), "{\"text\":\"Go to the garden\"}").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    void theLadderNeedsSigningIn() throws Exception {
        mvc.perform(get("/api/ladder")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ladder").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void someoneElsesStepIsNotFound() throws Exception {
        String danasStep = firstStepId(signUp("Dana"));
        String yossi = signUp("Yossi");
        mvc.perform(json(put("/api/ladder/" + danasStep), "{\"text\":\"mine now\"}").header("Authorization", "Bearer " + yossi))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/ladder/" + danasStep).header("Authorization", "Bearer " + yossi))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/ladder/" + danasStep + "/reached").header("Authorization", "Bearer " + yossi))
                .andExpect(status().isNotFound());
    }

    @Test
    void badDetailsAndTakenNames() throws Exception {
        mvc.perform(json(post("/api/account/register"), credentials("ab", "a-good-password")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/account/register"), credentials("Dana", "short")))
                .andExpect(status().isBadRequest());
        signUp("Dana");
        mvc.perform(json(post("/api/account/register"), credentials("dana", "a-good-password")))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordsAreLimited() throws Exception {
        signUp("Dana");
        for (int i = 0; i < 10; i++) {
            mvc.perform(json(post("/api/account/login"), credentials("Dana", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        // Even the right password waits now: the attacker doesn't learn which guess was right.
        mvc.perform(json(post("/api/account/login"), credentials("Dana", "a-good-password")))
                .andExpect(status().isTooManyRequests());
        // Someone from another address is not affected.
        address = "203.0.113.200";
        mvc.perform(json(post("/api/account/login"), credentials("Dana", "a-good-password")))
                .andExpect(status().isOk());
    }

    @Test
    void signUpsAreLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            signUp("person" + i);
        }
        mvc.perform(json(post("/api/account/register"), credentials("person5", "a-good-password")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deletingTheAccountEndsEverything() throws Exception {
        String token = signUp("Dana");
        firstStepId(token);
        mvc.perform(delete("/api/account").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertEquals(0, steps.count());
    }

    @Test
    void apiAnswersCarryTheSecurityHeaders() throws Exception {
        mvc.perform(get("/api/ladder"))
                .andExpect(header().string("Content-Security-Policy", SecurityHeadersFilter.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
