package demo.sop;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class Api {
    private final Store store;private final Compiler compiler;
    public Api(Store store,Compiler compiler){this.store=store;this.compiler=compiler;}
    public record SourceRequest(String source){}
    public record PublishRequest(Long revision){}
    @PostMapping("/validate") public Compiler.Result validate(@RequestBody SourceRequest request){Store.source(request.source());return compiler.compile(request.source());}
    @PutMapping("/drafts/{id}") public Map<String,Object> save(@PathVariable String id,@RequestBody SourceRequest request){return store.save(id,request.source());}
    @GetMapping("/drafts") public List<Map<String,Object>> drafts(){return store.drafts();}
    @GetMapping("/drafts/{id}") public Map<String,Object> draft(@PathVariable String id){return store.draft(id);}
    @PostMapping("/sops/{id}/publish") public Map<String,Object> publish(@PathVariable String id,@RequestBody PublishRequest request){return store.publish(id,request.revision());}
    @GetMapping("/sops") public List<Map<String,Object>> list(@RequestParam(required=false) String domain,@RequestParam(required=false) String risk){return store.list(domain,risk);}
    @GetMapping("/sops/{id}") public Map<String,Object> current(@PathVariable String id){return store.current(id);}
    @GetMapping("/sops/{id}/versions/{version}") public Map<String,Object> version(@PathVariable String id,@PathVariable int version){return store.version(id,version);}
}
