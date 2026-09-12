package demo.sop;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.nodes.*;

@Component
public class Compiler {
    public static final int MAX_BYTES = 65536;
    private static final List<String> SECTIONS = List.of("Intent (When to use)", "Do Not Use When", "Inputs Required", "Eligibility Rules", "Actions", "Boundaries", "Customer Messages");
    private static final Set<Tag> TAGS = Set.of(Tag.MAP, Tag.SEQ, Tag.STR, Tag.INT, Tag.FLOAT, Tag.BOOL, Tag.NULL);
    public record Issue(String code, String stage, String message, String path) {}
    public record Result(boolean valid, List<Issue> issues, Map<String,Object> content) {}

    public Result compile(String source) {
        Check c = new Check();
        if (source == null) { c.error("REQUIRED", "source", "Source must be a string."); return c.result(null); }
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) { c.error("SOURCE_TOO_LARGE", "source", "Source exceeds 65536 UTF-8 bytes."); return c.result(null); }
        List<String> lines = Arrays.asList(source.replace("\r\n", "\n").split("\n", -1));
        if (lines.isEmpty() || !lines.getFirst().equals("---")) { c.error("FRONT_MATTER", "source", "Begin with YAML front matter delimited by ---."); return c.result(null); }
        int end = 1;
        while (end < lines.size() && !lines.get(end).equals("---")) end++;
        if (end == lines.size()) { c.error("FRONT_MATTER", "source", "Missing closing front matter delimiter."); return c.result(null); }
        Object front = c.yaml(String.join("\n", lines.subList(1,end)), "front_matter");
        Map<String,List<String>> sections = new LinkedHashMap<>();
        String active = null;
        boolean fenced = false;
        for (int i=end+1;i<lines.size();i++) {
            String line=lines.get(i);
            if (!fenced && line.startsWith("## ")) {
                active=line.substring(3);
                if (!SECTIONS.contains(active)) c.error("UNKNOWN_SECTION", "sections."+active, "Unsupported section.");
                if (sections.containsKey(active)) c.error("DUPLICATE_SECTION", "sections."+active, "Section appears more than once.");
                else sections.put(active,new ArrayList<>());
            } else if (active == null) {
                if (!line.isBlank()) c.error("SECTION_STRUCTURE", "source", "Content must belong to a documented section.");
            } else {
                sections.get(active).add(line);
                if (line.startsWith("```")) fenced=!fenced;
            }
        }
        for (String name:SECTIONS) if (!sections.containsKey(name)) c.error("MISSING_SECTION", "sections."+name, "Required section is missing.");
        Map<String,Object> content=new LinkedHashMap<>();
        Map<String,Object> meta=c.object(front, "front_matter", Set.of("sop_id","title","owner_team","domain","intent","risk_level","max_autonomy"));
        if(meta!=null) {
            for(String key:List.of("sop_id","title","owner_team","domain","intent","risk_level","max_autonomy")) c.text(meta.get(key),"front_matter."+key);
            c.id(meta.get("sop_id"),"front_matter.sop_id","[A-Z][A-Z0-9-]{0,63}");
            c.choice(meta.get("domain"),"front_matter.domain","Billing","Support");
            c.choice(meta.get("intent"),"front_matter.intent","refund_duplicate_charge","answer_question");
            c.choice(meta.get("risk_level"),"front_matter.risk_level","low","medium");
            c.choice(meta.get("max_autonomy"),"front_matter.max_autonomy","assist");
            content.putAll(meta);
        }
        content.put("policy",Map.of("use_when",c.bullets(sections.get(SECTIONS.get(0)),"policy.use_when"),"do_not_use_when",c.bullets(sections.get(SECTIONS.get(1)),"policy.do_not_use_when")));
        String[] keys={"inputs","rules","actions","boundaries","customer_messages"};
        for(int i=0;i<keys.length;i++) content.put(keys[i],c.machine(sections.get(SECTIONS.get(i+2)),keys[i]));
        List<Map<String,Object>> inputs=c.objects(content.get("inputs"),"inputs",Set.of("name","type"),true);
        for(int i=0;i<inputs.size();i++) {
            var in=inputs.get(i); c.id(in.get("name"),"inputs["+i+"].name","[a-z][a-z0-9_]{0,63}"); c.choice(in.get("type"),"inputs["+i+"].type","number","boolean");
        }
        List<Map<String,Object>> actions=c.objects(content.get("actions"),"actions",Set.of("id","kind","description","max_amount"),true);
        for(int i=0;i<actions.size();i++) {
            var a=actions.get(i);String p="actions["+i+"]";
            c.id(a.get("id"),p+".id","[A-Za-z][A-Za-z0-9_-]{0,63}");c.choice(a.get("kind"),p+".kind","refund","escalate","human_assist");c.text(a.get("description"),p+".description");
            if (a.containsKey("max_amount")) c.positive(a.get("max_amount"),p+".max_amount");
            if(!"refund".equals(a.get("kind")) && a.containsKey("max_amount")) c.error("DISALLOWED_FIELD",p+".max_amount","Only refund actions can declare max_amount.");
        }
        List<Map<String,Object>> rules=c.objects(content.get("rules"),"rules",Set.of("id","conditions","action_ids"),true);
        for(int i=0;i<rules.size();i++) {
            var r=rules.get(i);String p="rules["+i+"]";c.id(r.get("id"),p+".id","[A-Za-z][A-Za-z0-9_-]{0,63}");
            var conditions=c.objects(r.get("conditions"),p+".conditions",Set.of("input","op","value"),true);
            for(int j=0;j<conditions.size();j++) {var v=conditions.get(j);String cp=p+".conditions["+j+"]";c.text(v.get("input"),cp+".input");c.choice(v.get("op"),cp+".op","eq","gt","lte"); if(!v.containsKey("value") || !(v.get("value") instanceof Boolean || finite(v.get("value")))) c.error("TYPE",cp+".value","Expected a finite number or boolean.");}
            var refs=c.list(r.get("action_ids"),p+".action_ids",true);for(int j=0;j<refs.size();j++) c.text(refs.get(j),p+".action_ids["+j+"]");
        }
        var boundaries=c.object(content.get("boundaries"),"boundaries",Set.of("escalation"));
        var escalations=c.objects(boundaries==null?null:boundaries.get("escalation"),"boundaries.escalation",Set.of("action_id","input","op","amount","target_action_id"),false);
        for(int i=0;i<escalations.size();i++) {var e=escalations.get(i);String p="boundaries.escalation["+i+"]";for(String k:List.of("action_id","input","target_action_id"))c.text(e.get(k),p+"."+k);c.choice(e.get("op"),p+".op","gt");c.positive(e.get("amount"),p+".amount");}
        var messages=c.object(content.get("customer_messages"),"customer_messages",Set.of("primary","escalation"));
        if(messages!=null)for(String k:List.of("primary","escalation"))c.text(messages.get(k),"customer_messages."+k);
        // Semantic validation needs structurally reliable collections, but missing limits remain discoverable.
        if(c.issues.isEmpty()) semantics(c,content,inputs,rules,actions,escalations);
        return c.result(content);
    }

    private void semantics(Check c,Map<String,Object> content,List<Map<String,Object>> inputs,List<Map<String,Object>> rules,List<Map<String,Object>> actions,List<Map<String,Object>> escalations) {
        c.semantic=true;
        var inputByName=c.index(inputs,"name","inputs");var actionById=c.index(actions,"id","actions");c.index(rules,"id","rules");
        for(int i=0;i<rules.size();i++) {
            var r=rules.get(i);String p="rules["+i+"]";
            List<?> conditions=(List<?>)r.get("conditions");
            for(int j=0;j<conditions.size();j++) {
                Map<?,?> condition=(Map<?,?>)conditions.get(j);String cp=p+".conditions["+j+"]";var in=inputByName.get(condition.get("input"));
                if(in==null)c.error("UNKNOWN_INPUT",cp+".input","Condition must reference a declared input.");
                else if("number".equals(in.get("type"))) {if(!finite(condition.get("value")))c.error("VALUE_TYPE",cp+".value","Numeric input requires a number.");}
                else {if(!(condition.get("value") instanceof Boolean))c.error("VALUE_TYPE",cp+".value","Boolean input requires a boolean.");if(!"eq".equals(condition.get("op")))c.error("OPERATOR_TYPE",cp+".op","Boolean inputs support eq only.");}
            }
            List<?> ids=(List<?>)r.get("action_ids");for(int j=0;j<ids.size();j++)if(!actionById.containsKey(ids.get(j)))c.error("UNKNOWN_ACTION",p+".action_ids["+j+"]","Rule must reference an existing action.");
        }
        for(int i=0;i<actions.size();i++)if("refund".equals(actions.get(i).get("kind"))&&!actions.get(i).containsKey("max_amount"))c.error("REFUND_LIMIT","actions["+i+"].max_amount","Refund action requires a positive numeric max_amount limit.");
        for(int i=0;i<escalations.size();i++) {
            var e=escalations.get(i);String p="boundaries.escalation["+i+"]";
            var a=actionById.get(e.get("action_id"));var target=actionById.get(e.get("target_action_id"));var in=inputByName.get(e.get("input"));
            if(a==null||!"refund".equals(a.get("kind")))c.error("REFUND_REFERENCE",p+".action_id","Boundary must reference an existing refund action.");
            if(target==null||!"escalate".equals(target.get("kind")))c.error("ESCALATE_REFERENCE",p+".target_action_id","Boundary target must be an existing escalate action.");
            if(in==null||!"number".equals(in.get("type")))c.error("NUMERIC_INPUT",p+".input","Boundary must reference a declared numeric input.");
        }
        var refunds=actions.stream().filter(a->"refund".equals(a.get("kind"))).toList();
        if("refund_duplicate_charge".equals(content.get("intent"))) {
            if(!"Billing".equals(content.get("domain")))c.error("REFUND_DOMAIN","front_matter.domain","Refund intent requires Billing domain.");
            if(refunds.size()!=1)c.error("REFUND_COUNT","actions","Refund intent requires exactly one refund action with a positive numeric limit.");
            var amount=inputByName.get("refund_amount");if(amount==null||!"number".equals(amount.get("type")))c.error("REFUND_INPUT","inputs","Refund intent requires numeric input refund_amount.");
            boolean matched=refunds.size()==1 && escalations.stream().anyMatch(e->{var a=refunds.getFirst();var target=actionById.get(e.get("target_action_id"));return Objects.equals(e.get("action_id"),a.get("id"))&&"refund_amount".equals(e.get("input"))&&"gt".equals(e.get("op"))&&sameNumber(e.get("amount"),a.get("max_amount"))&&target!=null&&"escalate".equals(target.get("kind"));});
            if(!matched)c.error("REFUND_ESCALATION","boundaries.escalation","Require an escalation boundary for refund_amount above the refund limit, targeting an escalate action.");
        } else if(!refunds.isEmpty())c.error("REFUND_INTENT","actions","answer_question does not permit refund actions.");
    }
    private static boolean finite(Object v) {return v instanceof Number n && Double.isFinite(n.doubleValue());}
    private static boolean sameNumber(Object a,Object b) {return finite(a)&&finite(b)&&new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;}

    private static class Check {
        final List<Issue> issues=new ArrayList<>();boolean semantic;
        void error(String code,String path,String message){issues.add(new Issue(code,semantic?"semantic":"structural",message,path));}
        Result result(Map<String,Object> content){issues.sort(Comparator.comparing(Issue::path).thenComparing(Issue::code));return new Result(issues.isEmpty(),List.copyOf(issues),issues.isEmpty()?content:null);}
        Object yaml(String source,String path){
            try {
                LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setNestingDepthLimit(20);options.setCodePointLimit(MAX_BYTES);
                Yaml yaml=new Yaml(new SafeConstructor(options));
                for(var event:yaml.parse(new StringReader(source)))if(event instanceof AliasEvent)throw new IllegalArgumentException();
                Node node=yaml.compose(new StringReader(source));inspect(node,0);
                return yaml.load(source);
            }catch(RuntimeException e){error("YAML_INVALID",path,"Invalid YAML: use unique keys, supported scalar types, no aliases/tags, and at most 20 collection levels.");return null;}
        }
        void inspect(Node n,int depth){
            if(n==null)return;
            if(!TAGS.contains(n.getTag()))throw new IllegalArgumentException();
            if(n instanceof CollectionNode<?> && ++depth>20)throw new IllegalArgumentException();
            if(n instanceof MappingNode m)for(var pair:m.getValue()){inspect(pair.getKeyNode(),depth);inspect(pair.getValueNode(),depth);}
            if(n instanceof SequenceNode s)for(var child:s.getValue())inspect(child,depth);
        }
        Map<String,Object> object(Object v,String p,Set<String> keys){
            if(!(v instanceof Map<?,?> m)){error("TYPE",p,"Expected an object.");return null;}
            Map<String,Object> result=new LinkedHashMap<>();
            for(var e:m.entrySet())if(!(e.getKey() instanceof String k)){error("UNKNOWN_FIELD",p,"Object keys must be documented strings.");}else{if(!keys.contains(k))error("UNKNOWN_FIELD",p+"."+k,"Unknown field.");result.put(k,e.getValue());}
            return result;
        }
        List<?> list(Object v,String p,boolean nonempty){if(!(v instanceof List<?> l)){error("TYPE",p,"Expected a list.");return List.of();}if(nonempty&&l.isEmpty())error("REQUIRED",p,"At least one entry is required.");return l;}
        List<Map<String,Object>> objects(Object v,String p,Set<String> keys,boolean nonempty){List<?> l=list(v,p,nonempty);List<Map<String,Object>> out=new ArrayList<>();for(int i=0;i<l.size();i++){var m=object(l.get(i),p+"["+i+"]",keys);out.add(m==null?new LinkedHashMap<>():m);}return out;}
        void text(Object v,String p){if(!(v instanceof String s)||s.isBlank())error("TEXT",p,"Expected nonempty text.");}
        void id(Object v,String p,String regex){if(!(v instanceof String s)||!Pattern.matches(regex,s))error("IDENTIFIER",p,"Invalid identifier.");}
        void choice(Object v,String p,String... values){if(!(v instanceof String)||!Arrays.asList(values).contains(v))error("ENUM",p,"Expected one of: "+String.join(", ",values)+".");}
        void positive(Object v,String p){if(!finite(v)||new BigDecimal(v.toString()).signum()<=0)error("POSITIVE_NUMBER",p,"Expected a positive finite number.");}
        List<String> bullets(List<String> lines,String p){List<String> out=new ArrayList<>();if(lines!=null)for(String line:lines){if(line.isBlank())continue;if((line.startsWith("- ")||line.startsWith("* "))&&!line.substring(2).isBlank())out.add(line.substring(2).trim());else error("BULLET_STRUCTURE",p,"Use plain nonempty bullet lines only.");}if(out.isEmpty())error("REQUIRED",p,"At least one bullet is required.");return out;}
        Object machine(List<String> lines,String p){if(lines==null)return null;var nonblank=new ArrayList<>(lines);while(!nonblank.isEmpty()&&nonblank.getFirst().isBlank())nonblank.removeFirst();while(!nonblank.isEmpty()&&nonblank.getLast().isBlank())nonblank.removeLast();if(nonblank.size()<3||!nonblank.getFirst().equals("```yaml")||!nonblank.getLast().equals("```")||nonblank.subList(1,nonblank.size()-1).stream().anyMatch(l->l.startsWith("```"))){error("FENCE_STRUCTURE",p,"Use exactly one fenced yaml block with no other content.");return null;}return yaml(String.join("\n",nonblank.subList(1,nonblank.size()-1)),p);}
        Map<Object,Map<String,Object>> index(List<Map<String,Object>> values,String key,String p){Map<Object,Map<String,Object>> out=new HashMap<>();for(int i=0;i<values.size();i++){var v=values.get(i);if(out.putIfAbsent(v.get(key),v)!=null)error("DUPLICATE_ID",p+"["+i+"]."+key,"Identifier must be unique.");}return out;}
    }
}
