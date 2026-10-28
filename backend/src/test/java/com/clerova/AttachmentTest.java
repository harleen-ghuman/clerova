package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.domain.ActionType;
import com.clerova.domain.Category;
import com.clerova.domain.Priority;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.clerova.email.InboundEmailProcessor;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttachmentTest {

    private static final String WEBHOOK_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("postmark:webhook-secret".getBytes(StandardCharsets.UTF_8));

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InboundEmailProcessor processor;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private FileStorage fileStorage;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, byte[]> stored = new ConcurrentHashMap<>();

    private RequestPostProcessor user;
    private String forwardingAddress;

    @BeforeEach
    void setUp() throws Exception {
        ExtractionResult extraction = new ExtractionResult();
        extraction.category = Category.MAINTENANCE;
        extraction.priority = Priority.HIGH;
        extraction.summary = "Leaking sink";
        extraction.address = "12 Oak St";
        when(aiService.extract(anyString())).thenReturn(extraction);

        ProposedActionResult action = new ProposedActionResult();
        action.actionType = ActionType.CREATE_MAINTENANCE_REQUEST;
        action.reasoning = "Reasoning";
        action.draftedResponse = "Thanks";
        when(aiService.proposeAction(any(), any())).thenReturn(action);

        when(fileStorage.isEnabled()).thenReturn(true);
        doAnswer(call -> {
            stored.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(fileStorage).put(anyString(), any(), anyString());
        doAnswer(call -> {
            byte[] content = stored.get(call.<String>getArgument(0));
            if (content == null) {
                throw new FileStorageException("missing");
            }
            return content;
        }).when(fileStorage).get(anyString());

        user = onboardNewUser(mockMvc);
        forwardingAddress = objectMapper.readTree(mockMvc.perform(get("/api/email-settings").with(user))
                .andReturn().getResponse().getContentAsString()).get("forwardingAddress").asText();
    }

    /** Delivers an email with the given files (name, type, content) and returns its inbox entry. */
    private JsonNode deliver(String messageId, Object[]... files) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", "tina@example.com");
        root.putObject("FromFull").put("Email", "tina@example.com").put("Name", "Tenant Tina");
        root.putArray("ToFull").addObject().put("Email", forwardingAddress);
        root.put("OriginalRecipient", forwardingAddress);
        root.put("Subject", "Sink leaking");
        root.put("TextBody", "Water under the sink, photo attached.");
        root.putArray("Headers").addObject().put("Name", "Message-ID").put("Value", messageId);

        ArrayNode attachments = root.putArray("Attachments");
        for (Object[] file : files) {
            byte[] content = (byte[]) file[2];
            attachments.addObject()
                    .put("Name", (String) file[0])
                    .put("ContentType", (String) file[1])
                    .put("ContentLength", content.length)
                    .put("Content", Base64.getEncoder().encodeToString(content));
        }

        mockMvc.perform(post("/api/inbound/postmark")
                        .header("Authorization", WEBHOOK_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        return objectMapper.readTree(mockMvc.perform(get("/api/inbox").with(user))
                .andReturn().getResponse().getContentAsString()).get(0);
    }

    private static Object[] file(String name, String type, byte[] content) {
        return new Object[]{name, type, content};
    }

    @Test
    void photosAreKeptAndOpenOnlyForTheirWorkspace() throws Exception {
        JsonNode message = deliver("<files1@x>",
                file("sink.jpg", "image/jpeg", PHOTO),
                file("lease.html", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));

        assertThat(message.get("attachments").get(0).asText()).isEqualTo("sink.jpg");
        JsonNode photo = message.get("files").get(0);
        JsonNode page = message.get("files").get(1);
        assertThat(photo.get("available").asBoolean()).isTrue();
        assertThat(photo.get("sizeBytes").asLong()).isEqualTo(PHOTO.length);

        // A photo opens in the browser
        MvcResult result = mockMvc.perform(get("/api/attachments/{id}", photo.get("id").asText()).with(user))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PHOTO);
        assertThat(result.getResponse().getHeader("Content-Disposition")).startsWith("inline");

        // Anything that could run as a page only downloads
        mockMvc.perform(get("/api/attachments/{id}", page.get("id").asText()).with(user))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")));

        // The files show on the request's conversation too
        String workItemId = message.get("workItemId").asText();
        mockMvc.perform(get("/api/work-items/{id}/conversation", workItemId).with(user))
                .andExpect(jsonPath("$[0].files[0].fileName").value("sink.jpg"))
                .andExpect(jsonPath("$[0].files[0].available").value(true));

        // Another workspace can't open it, and nobody signed out can
        mockMvc.perform(get("/api/attachments/{id}", photo.get("id").asText()).with(onboardNewUser(mockMvc)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/attachments/{id}", photo.get("id").asText()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/company-info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void largeFilesAndStorageFailuresKeepTheNameAndTheEmail() throws Exception {
        byte[] large = new byte[10 * 1024 * 1024 + 1];

        JsonNode message = deliver("<files2@x>",
                file("video.mov", "video/quicktime", large),
                file("sink.jpg", "image/jpeg", PHOTO));

        assertThat(message.get("files").get(0).get("available").asBoolean()).isFalse();
        assertThat(message.get("files").get(1).get("available").asBoolean()).isTrue();

        mockMvc.perform(get("/api/attachments/{id}", message.get("files").get(0).get("id").asText()).with(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Only the name of this file was kept. It's in the sender's original email."));

        // Storage down: the email still arrives, with the file's name
        doThrow(new FileStorageException("down")).when(fileStorage).put(anyString(), any(), anyString());

        JsonNode failed = deliver("<files3@x>", file("sink2.jpg", "image/jpeg", PHOTO));
        assertThat(failed.get("status").asText()).isEqualTo("PROCESSED");
        assertThat(failed.get("files").get(0).get("fileName").asText()).isEqualTo("sink2.jpg");
        assertThat(failed.get("files").get(0).get("available").asBoolean()).isFalse();
    }

    @Test
    void theLargestEmailPostmarkAcceptsStillArrives() throws Exception {
        // Postmark allows up to 35 MB per email; base64 makes it about 47 MB of JSON
        byte[] video = new byte[30 * 1024 * 1024];

        JsonNode message = deliver("<files5@x>",
                file("walkthrough.mov", "video/quicktime", video),
                file("sink.jpg", "image/jpeg", PHOTO));

        assertThat(message.get("files").get(0).get("available").asBoolean()).isFalse();
        assertThat(message.get("files").get(1).get("available").asBoolean()).isTrue();
    }

    @Test
    void withoutStorageOnlyNamesAreKept() throws Exception {
        when(fileStorage.isEnabled()).thenReturn(false);

        JsonNode message = deliver("<files4@x>", file("sink.jpg", "image/jpeg", PHOTO));

        assertThat(message.get("files").get(0).get("available").asBoolean()).isFalse();
        assertThat(stored).isEmpty();
    }
}
