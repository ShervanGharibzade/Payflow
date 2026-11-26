package com.payflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "firstName", "Ada", "lastName", "Lovelace",
                        "email", email, "password", "correct-horse-battery"))))
                .andExpect(status().isCreated());
    }

    private Map<?, ?> login(String email, String pw, int expected) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", pw))))
                .andExpect(status().is(expected)).andReturn();
        return expected == 200 ? json.readValue(r.getResponse().getContentAsString(), Map.class) : null;
    }

    private int refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void registerLoginAndMe() throws Exception {
        register("ada@example.com");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("firstName", "A", "lastName", "B",
                        "email", "ADA@example.com", "password", "correct-horse-battery"))))
                .andExpect(status().isConflict());

        Map<?, ?> tokens = login("Ada@Example.com", "correct-horse-battery", 200);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.get("accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void badCredentialsAreRejectedUniformly() throws Exception {
        register("bob@example.com");
        login("bob@example.com", "wrong-password", 401);
        login("nobody@example.com", "whatever-pass", 401);
    }

    @Test
    void refreshRotatesAndReuseRevokesEverything() throws Exception {
        register("eve@example.com");
        Map<?, ?> first = login("eve@example.com", "correct-horse-battery", 200);
        String r1 = (String) first.get("refreshToken");

        MvcResult res = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", r1))))
                .andExpect(status().isOk()).andReturn();
        String r2 = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("refreshToken");

        // replaying the old token = theft -> 401 and the new one dies too
        org.junit.jupiter.api.Assertions.assertEquals(401, refresh(r1));
        org.junit.jupiter.api.Assertions.assertEquals(401, refresh(r2));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        register("kim@example.com");
        String r = (String) login("kim@example.com", "correct-horse-battery", 200).get("refreshToken");
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", r))))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(401, refresh(r));
    }

    @Test
    void jwksExposesOnlyPublicKey() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
