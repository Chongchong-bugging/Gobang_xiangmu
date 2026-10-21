package com.gobang.config;

import com.gobang.controller.UserController;
import com.gobang.dto.ApiResponse;
import com.gobang.dto.UserResponse;
import com.gobang.service.UserService;
import com.gobang.security.RequestRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserService userService;
    @MockitoBean private RequestRateLimiter rateLimiter;

    @Test
    void protectedEndpointReturnsAJson401ForAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/user/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void ordinaryUserCannotReadManagementMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(user("player").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointIsPublicAndCreatesTheAxiosCookie() throws Exception {
        mockMvc.perform(get("/api/user/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void mutationWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"player\",\"password\":\"secure-pass\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void anonymousRegistrationWithCsrfTokenReachesTheController() throws Exception {
        UserResponse user = new UserResponse(1L, "player", null);
        when(userService.register(eq("player"), eq("secure-pass")))
                .thenReturn(ApiResponse.success("注册成功", user));

        MvcResult csrfResult = mockMvc.perform(get("/api/user/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = Objects.requireNonNull(csrfResult.getResponse().getCookie("XSRF-TOKEN"));

        mockMvc.perform(post("/api/user/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"player\",\"password\":\"secure-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("player"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }
}
