package demo.sop;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import static demo.sop.Errors.fail;

@Repository
public class Store {
    private final JdbcTemplate db;private final TransactionTemplate tx;private final Compiler compiler;private final ObjectMapper json;
    public Store(JdbcTemplate db,TransactionTemplate tx,Compiler compiler,ObjectMapper json){this.db=db;this.tx=tx;this.compiler=compiler;this.json=json;}
    public static void id(String id){if(id==null||!id.matches("[A-Z][A-Z0-9-]{0,63}"))throw fail(400,"BAD_ID","Invalid SOP identifier.");}
    public static void source(String source){if(source==null)throw fail(400,"BAD_REQUEST","source must be a string.");if(source.getBytes(StandardCharsets.UTF_8).length>Compiler.MAX_BYTES)throw fail(413,"SOURCE_TOO_LARGE","Source exceeds 65536 UTF-8 bytes.");}
    public Map<String,Object> save(String id,String source){id(id);source(source);return tx.execute(status->{db.update("INSERT INTO drafts(sop_id, source, revision) VALUES (?, ?, 1) ON CONFLICT (sop_id) DO UPDATE SET source=EXCLUDED.source, revision=drafts.revision+1, failed_revision=NULL",id,source);var draft=draft(id);return Map.of("sop_id",id,"revision",draft.get("revision"),"source",source);});}
    public List<Map<String,Object>> drafts(){return db.queryForList("SELECT sop_id, revision, failed_revision, current_version FROM drafts ORDER BY sop_id");}
    public Map<String,Object> draft(String id){id(id);var rows=db.queryForList("SELECT sop_id, source, revision, failed_revision, current_version FROM drafts WHERE sop_id=?",id);if(rows.isEmpty())throw fail(404,"NOT_FOUND","Draft not found.");return rows.getFirst();}
    private record Publication(Map<String,Object> snapshot,List<Compiler.Issue> issues){}
    public Map<String,Object> publish(String id,Long revision){
        id(id);if(revision==null||revision<1)throw fail(400,"BAD_REQUEST","revision must be a positive integer.");
        Publication result=tx.execute(status->{
            var rows=db.queryForList("SELECT * FROM drafts WHERE sop_id=? FOR UPDATE",id);if(rows.isEmpty())throw fail(404,"NOT_FOUND","Draft not found.");var d=rows.getFirst();
            if(((Number)d.get("revision")).longValue()!=revision)throw fail(409,"STALE_REVISION","Save or reopen the latest draft before publishing.");
            if(db.queryForObject("SELECT count(*) FROM publications WHERE sop_id=? AND draft_revision=?",Integer.class,id,revision)>0)throw fail(409,"ALREADY_PUBLISHED","This saved revision is already published.");
            String source=(String)d.get("source");var compiled=compiler.compile(source);var issues=new ArrayList<>(compiled.issues());
            if(compiled.valid()&&!id.equals(compiled.content().get("sop_id")))issues.add(new Compiler.Issue("ID_MISMATCH","semantic","Source sop_id must match the saved draft identifier.","front_matter.sop_id"));
            if(!issues.isEmpty()){db.update("UPDATE drafts SET failed_revision=? WHERE sop_id=?",revision,id);return new Publication(null,issues);}
            int version=d.get("current_version")==null?1:((Number)d.get("current_version")).intValue()+1;
            Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("sop_id",id);snapshot.put("version",version);snapshot.put("published_at",Instant.now().toString());snapshot.put("content",compiled.content());
            db.update("INSERT INTO publications(sop_id,version,draft_revision,source,snapshot) VALUES (?,?,?,?,?::jsonb)",id,version,revision,source,encode(snapshot));
            db.update("UPDATE drafts SET current_version=?, failed_revision=NULL WHERE sop_id=?",version,id);
            return new Publication(current(id),List.of());
        });
        if(!result.issues().isEmpty())throw new Errors.Failure(422,"PUBLICATION_INVALID","Publication failed; the previous published version remains available if one exists.",result.issues());
        return result.snapshot();
    }
    public List<Map<String,Object>> list(String domain,String risk){
        if(domain!=null&&!List.of("Billing","Support").contains(domain))throw fail(400,"INVALID_FILTER","domain must be Billing or Support.");
        if(risk!=null&&!List.of("low","medium").contains(risk))throw fail(400,"INVALID_FILTER","risk must be low or medium.");
        String sql="SELECT p.sop_id,p.version,p.snapshot->'content'->>'title' AS title,p.snapshot->'content'->>'domain' AS domain,p.snapshot->'content'->>'risk_level' AS risk FROM publications p JOIN drafts d ON d.sop_id=p.sop_id AND d.current_version=p.version";
        List<Object> args=new ArrayList<>();List<String> filters=new ArrayList<>();if(domain!=null){filters.add("p.snapshot->'content'->>'domain'=?");args.add(domain);}if(risk!=null){filters.add("p.snapshot->'content'->>'risk_level'=?");args.add(risk);}if(!filters.isEmpty())sql+=" WHERE "+String.join(" AND ",filters);return db.queryForList(sql+" ORDER BY p.sop_id",args.toArray());
    }
    public Map<String,Object> current(String id){id(id);return read("SELECT p.snapshot::text FROM publications p JOIN drafts d ON d.sop_id=p.sop_id AND d.current_version=p.version WHERE p.sop_id=?",id);}
    public Map<String,Object> version(String id,int version){id(id);if(version<1)throw fail(400,"BAD_REQUEST","version must be positive.");return read("SELECT snapshot::text FROM publications WHERE sop_id=? AND version=?",id,version);}
    private Map<String,Object> read(String sql,Object... args){var rows=db.queryForList(sql,String.class,args);if(rows.isEmpty())throw fail(404,"NOT_FOUND","Published SOP not found.");try{return json.readValue(rows.getFirst(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
