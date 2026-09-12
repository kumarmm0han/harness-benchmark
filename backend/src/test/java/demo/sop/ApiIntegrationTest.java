package demo.sop;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.profiles.active=demo")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_URL",matches=".+")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Store store;@Autowired JdbcTemplate db;@Autowired Seed seed;
    String id(){return "TEST-"+UUID.randomUUID().toString().toUpperCase();}
    String source(String id){return CompilerTest.template().replace("BILL-REFUND-001",id);}
    JsonNode call(MockHttpServletRequestBuilder request,String user,Object body,int status)throws Exception {
        if(user!=null)request.header("X-Demo-User",user);
        if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
        var result=mvc.perform(request).andReturn();assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);return json.readTree(result.getResponse().getContentAsString());
    }
    JsonNode author(MockHttpServletRequestBuilder request,Object body,int status)throws Exception{return call(request,"demo-author",body,status);}
    @Test void identityAndMalformedRequests()throws Exception {
        call(get("/api/v1/sops"),null,null,401);call(get("/api/v1/sops"),"unknown",null,401);
        for(var request:List.of(get("/api/v1/drafts"),get("/api/v1/drafts/X"),get("/api/v1/sops/X/versions/1"),post("/api/v1/validate"),put("/api/v1/drafts/X"),post("/api/v1/sops/X/publish")))call(request,"demo-consumer",null,403);
        author(post("/api/v1/validate"),Map.of("source",42),400);author(post("/api/v1/validate"),Map.of("source","x","extra",true),400);author(post("/api/v1/validate"),Map.of(),400);
        author(post("/api/v1/sops/X/publish"),Map.of("revision","1"),400);author(post("/api/v1/sops/X/publish"),Map.of("revision",1.2),400);
        author(get("/api/v1/sops?risk=high"),null,400);author(get("/api/v1/sops?domain=Other"),null,400);
        author(put("/api/v1/drafts/X"),Map.of("source","é".repeat(32769)),413);
        author(get("/api/v1/sops").header("Origin","https://evil.test"),null,403);
        var preflight=mvc.perform(options("/api/v1/sops").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","GET")).andReturn();assertThat(preflight.getResponse().getStatus()).isEqualTo(204);assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }
    @Test void completeLifecycleAndInvalidReplacement()throws Exception {
        String id=id();String s=source(id);var saved=author(put("/api/v1/drafts/"+id),Map.of("source",s),200);assertThat(saved.get("revision").asInt()).isEqualTo(1);
        call(get("/api/v1/sops/"+id),"demo-consumer",null,404);
        var preview=author(post("/api/v1/validate"),Map.of("source",s),200);assertThat(preview.get("valid").asBoolean()).isTrue();
        var v1=author(post("/api/v1/sops/"+id+"/publish"),Map.of("revision",1),200);assertThat(v1.get("content")).isEqualTo(preview.get("content"));
        assertThat(call(get("/api/v1/sops/"+id),"demo-consumer",null,200)).isEqualTo(v1);
        assertThat(author(get("/api/v1/sops?domain=Billing&risk=medium"),null,200).toString()).contains(id);
        assertThat(author(get("/api/v1/sops?domain=Support&risk=medium"),null,200).toString()).doesNotContain(id);
        author(post("/api/v1/sops/"+id+"/publish"),Map.of("revision",1),409);
        String invalid=s.replace("  max_amount: 200\n","").replace("escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2","escalation: []");
        author(put("/api/v1/drafts/"+id),Map.of("source",invalid),200);
        author(post("/api/v1/sops/"+id+"/publish"),Map.of("revision",1),409);
        var failed=author(post("/api/v1/sops/"+id+"/publish"),Map.of("revision",2),422);assertThat(failed.toString()).contains("REFUND_LIMIT","REFUND_ESCALATION");
        assertThat(author(get("/api/v1/drafts/"+id),null,200).get("failed_revision").asInt()).isEqualTo(2);
        assertThat(author(get("/api/v1/sops/"+id),null,200)).isEqualTo(v1);
        var before=author(get("/api/v1/drafts/"+id),null,200);author(post("/api/v1/validate"),Map.of("source","bad"),200);assertThat(author(get("/api/v1/drafts/"+id),null,200)).isEqualTo(before);
        author(put("/api/v1/drafts/"+id),Map.of("source",s.replace("Refund for Duplicate Charge","Revised refund policy")),200);
        assertThat(author(get("/api/v1/drafts/"+id),null,200).get("failed_revision").isNull()).isTrue();
        var v2=author(post("/api/v1/sops/"+id+"/publish"),Map.of("revision",3),200);assertThat(v2.get("version").asInt()).isEqualTo(2);
        assertThat(author(get("/api/v1/sops/"+id+"/versions/1"),null,200)).isEqualTo(v1);
        assertThat(db.queryForObject("SELECT source FROM publications WHERE sop_id=? AND version=1",String.class,id)).isEqualTo(s);
        assertThatThrownBy(()->db.update("UPDATE publications SET source='changed' WHERE sop_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->db.update("DELETE FROM publications WHERE sop_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void concurrencyAndRollback()throws Exception {
        String id=id();store.save(id,source(id));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);Callable<Integer> publish=()->{start.await();try{store.publish(id,1L);return 200;}catch(Errors.Failure e){return e.status;}};
            var a=pool.submit(publish);var b=pool.submit(publish);start.countDown();assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM publications WHERE sop_id=?",Integer.class,id)).isEqualTo(1);
        // Force a database failure after snapshot insertion; the entire transaction must roll back.
        store.save(id,source(id));
        db.execute("CREATE FUNCTION fail_pointer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.sop_id='"+id+"' AND NEW.current_version=2 THEN RAISE EXCEPTION 'test'; END IF; RETURN NEW; END; $$");
        db.execute("CREATE TRIGGER test_fail_pointer BEFORE UPDATE ON drafts FOR EACH ROW EXECUTE FUNCTION fail_pointer()");
        try{assertThatThrownBy(()->store.publish(id,2L)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(store.current(id).get("version")).isEqualTo(1);assertThat(db.queryForObject("SELECT count(*) FROM publications WHERE sop_id=?",Integer.class,id)).isEqualTo(1);}finally{db.execute("DROP TRIGGER test_fail_pointer ON drafts");db.execute("DROP FUNCTION fail_pointer()");}
        assertThat(store.publish(id,2L).get("version")).isEqualTo(2);
    }
    @Test void seedPreservesEditsAndIdMismatch()throws Exception {
        store.save("BILL-REFUND-001","user edit");seed.run();assertThat(store.draft("BILL-REFUND-001").get("source")).isEqualTo("user edit");
        String id=id();store.save(id,CompilerTest.template());author(post("/api/v1/sops/"+id+"/publish"),Map.of("revision",1),422);call(get("/api/v1/sops/"+id),"demo-consumer",null,404);assertThat(store.draft(id).get("failed_revision")).isEqualTo(1L);
    }
}
