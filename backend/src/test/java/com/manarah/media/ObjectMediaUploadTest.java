package com.manarah.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.common.storage.FileStorage;
import com.manarah.billing.PaymentSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true", "manarah.demo.password=manarah123", "manarah.storage.provider=s3"})
@AutoConfigureMockMvc
class ObjectMediaUploadTest {
    private static final String RUN="object-media-"+UUID.randomUUID();
    private static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){com.manarah.TestDatabase.register(p,RUN);}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PublicImageRepository images;
    @Autowired PaymentSubmissionRepository submissions;
    @Autowired com.manarah.academy.TeacherAcademyRepository academies;
    @MockBean FileStorage storage;
    private final Map<String,byte[]> objects=new HashMap<>();
    @BeforeEach void objectStore(){
        when(storage.store(anyLong(),anyString(),any())).thenAnswer(inv->{
            String key="t"+inv.getArgument(0)+"/"+inv.getArgument(1)+"/"+UUID.randomUUID()+"_image.png";
            objects.put(key,((org.springframework.web.multipart.MultipartFile)inv.getArgument(2)).getBytes());return key;
        });
        when(storage.open(anyString())).thenAnswer(inv->new ByteArrayResource(objects.get(inv.getArgument(0))));
    }
    JsonNode call(MockHttpServletRequestBuilder req,String token,Object body)throws Exception{
        if(token!=null)req.header("Authorization","Bearer "+token);
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String login(String email,String password)throws Exception{return call(post("/api/auth/login"),null,Map.of("email",email,"password",password)).path("accessToken").asText();}
    MockMultipartFile image(){return new MockMultipartFile("file","image.png","image/png",PNG);}
    String register(String email,String phone)throws Exception{return call(post("/api/public/register"),null,Map.of("fullName","Storage QA student","email",email,"phone",phone,"password","TestPass123!","tenantSlug","object-media" )).path("accessToken").asText();}

    @Test void imagesTeacherPagesAndPrivateReceiptsUseObjectsWithoutLosingAccessRules()throws Exception{
        String admin=login("admin@manarah.io","manarah123");
        long academy=call(post("/api/academies"),admin,Map.of("name","Storage QA teacher","slug","object-media","username","object.teacher","password","TestPass123!")).path("id").asLong();
        var academyRecord=academies.findById(academy).orElseThrow();
        academyRecord.setPublished(true);academies.saveAndFlush(academyRecord);
        String teacher=login("object.teacher","TestPass123!");
        JsonNode uploaded=call(multipart("/api/images").file(image()),teacher,null);
        assertThat(images.findById(uploaded.path("id").asLong()).orElseThrow().getData()).startsWith("object:image/png:t");
        mvc.perform(get(uploaded.path("url").asText())).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        for(String slot:List.of("photo","cover")){
            call(multipart("/api/academies/"+academy+"/images/"+slot).file(image()),teacher,null);
            mvc.perform(get("/api/public/academies/object-media/images/"+slot)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        }
        JsonNode page=call(multipart("/api/academies/"+academy+"/gallery").file(image()),teacher,null);
        mvc.perform(get(page.path("gallery").get(0).asText())).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        academyRecord=academies.findById(academy).orElseThrow();
        academyRecord.setPublished(false);academies.saveAndFlush(academyRecord);
        JsonNode editor=call(get("/api/admin/teachers/"+academy),admin,null);
        assertThat(editor.path("photoUrl").asText()).startsWith("data:image/png;base64,");
        academyRecord.setPublished(true);academies.saveAndFlush(academyRecord);
        call(post("/api/courses"),teacher,Map.of("title","Storage QA course","price",100,"subject","الرياضيات","grade","الصف الثالث الثانوي"));
        long plan=call(put("/api/plans"),teacher,Map.of("year","الصف الثالث الثانوي","subject","الرياضيات","price",100,"months",1)).path("id").asLong();
        String student=register("object.one@example.com","01011113333"),other=register("object.two@example.com","01011114444");
        long invoice=call(post("/api/me/invoices"),student,Map.of("planId",plan,"months",1,"method","VODAFONE_CASH")).path("id").asLong();
        call(multipart("/api/me/invoices/"+invoice+"/receipt").file(new MockMultipartFile("receipt","receipt.png","image/png",PNG)).param("reference","QA-ONLY"),student,null);
        var receipt=submissions.findFirstByInvoiceIdOrderByIdDesc(invoice).orElseThrow();
        assertThat(receipt.getReceiptData()).startsWith("object:image/png:t").contains("/receipts/");
        mvc.perform(get("/api/me/payments/"+receipt.getId()+"/receipt").header("Authorization","Bearer "+student)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mvc.perform(get("/api/admin/payments/"+receipt.getId()+"/receipt").header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mvc.perform(get("/api/me/payments/"+receipt.getId()+"/receipt").header("Authorization","Bearer "+other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/payments/"+receipt.getId()+"/receipt")).andExpect(status().isUnauthorized());
    }
}
