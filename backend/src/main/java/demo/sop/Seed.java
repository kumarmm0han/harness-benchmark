package demo.sop;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
@Profile("demo")
public class Seed implements CommandLineRunner {
    private final JdbcTemplate db;
    public Seed(JdbcTemplate db){this.db=db;}
    @Override public void run(String... args)throws Exception{String source=new String(getClass().getResourceAsStream("/template.md").readAllBytes(),StandardCharsets.UTF_8);db.update("INSERT INTO drafts(sop_id,source,revision) VALUES ('BILL-REFUND-001',?,1) ON CONFLICT DO NOTHING",source);}
}
