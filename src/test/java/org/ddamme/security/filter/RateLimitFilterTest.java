package org.ddamme.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.ddamme.security.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateLimitFilterTest {

  private MockMvc mockMvc;

  private RateLimitProperties props;

  private ObjectMapper mapper;

  @RestController
  @RequestMapping("/api/v1/files")
  static class TestController {
    @GetMapping("/download/{id}")
    public String download(@PathVariable Long id) {
      return "ok";
    }

    @RequestMapping(value = "/download/{id}", method = RequestMethod.HEAD)
    public void head(@PathVariable Long id) {
      // no body
    }
  }

  @BeforeEach
  void setup() {
    props = new RateLimitProperties();
    props.setPerMinute(
        Map.of(
            "download", 1 // allow one request per minute → second should be 429
            ));
    props.setMessage("Please slow down");
    props.setSendRetryAfter(true);
    props.setRetryAfter(Duration.ofSeconds(30));

    mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    var filter = new RateLimitFilter(props, mapper, new SimpleMeterRegistry());

    mockMvc = MockMvcBuilders.standaloneSetup(new TestController()).addFilter(filter).build();
  }

  @Test
  void secondDownloadRequest_returns429_withRateLimitHeaders() throws Exception {
    // 1st request → should pass
    mockMvc
        .perform(get("/api/v1/files/download/1"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));

    // 2nd request → should be rate limited
    var mvcResult =
        mockMvc
            .perform(get("/api/v1/files/download/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("X-RateLimit-Limit"))
            .andExpect(header().exists("X-RateLimit-Remaining"))
            .andExpect(header().exists("X-RateLimit-Reset"))
            .andExpect(header().string("X-RateLimit-Window", "60"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    // quick sanity checks on header values
    var limit = mvcResult.getResponse().getHeader("X-RateLimit-Limit");
    var remaining = mvcResult.getResponse().getHeader("X-RateLimit-Remaining");
    var reset = mvcResult.getResponse().getHeader("X-RateLimit-Reset");
    var retryAfter = mvcResult.getResponse().getHeader(HttpHeaders.RETRY_AFTER);

    assertThat(limit).isEqualTo("1");
    assertThat(remaining).isNotNull(); // typically "0" after blocking
    assertThat(Integer.parseInt(remaining)).isBetween(0, 1);
    assertThat(Long.parseLong(reset)).isGreaterThanOrEqualTo(0L);
    assertThat(Long.parseLong(retryAfter)).isGreaterThan(0L);

    // body shape check (optional)
    var body = mvcResult.getResponse().getContentAsString();
    assertThat(body)
        .contains("Too Many Requests")
        .contains("Please slow down")
        .contains("\"status\":429");
  }

  @Test
  void secondHeadRequest_returns429_withNoBody_andContentLengthZero() throws Exception {
    // 1st HEAD → should pass
    mockMvc.perform(head("/api/v1/files/download/1")).andExpect(status().isOk());

    // 2nd HEAD → should be rate limited, with empty body
    var res =
        mockMvc
            .perform(head("/api/v1/files/download/1"))
            .andExpect(status().isTooManyRequests())
            .andReturn()
            .getResponse();

    // Filter sets Content-Length: 0 and does not write a body for HEAD
    assertThat(res.getContentLength()).isEqualTo(0);
    assertThat(res.getContentAsString()).isEmpty();
  }

  @Test
  void retryAfter_omittedWhenDisabled() throws Exception {
    // rebuild MockMvc with Retry-After disabled
    props.setSendRetryAfter(false);
    var filter = new RateLimitFilter(props, mapper, new SimpleMeterRegistry());
    mockMvc = MockMvcBuilders.standaloneSetup(new TestController()).addFilter(filter).build();

    // exhaust the single token
    mockMvc.perform(get("/api/v1/files/download/1")).andExpect(status().isOk());

    // second call → 429 without Retry-After
    mockMvc
        .perform(get("/api/v1/files/download/1"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
  }
}
