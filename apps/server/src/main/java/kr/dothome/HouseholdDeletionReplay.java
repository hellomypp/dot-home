package kr.dothome;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;

public final class HouseholdDeletionReplay {
    private HouseholdDeletionReplay(){}

    public static void main(String[] args){
        if(args.length!=1||!List.of("--plan","--apply").contains(args[0])){
            System.err.println("Specify --plan or --apply.");
            System.exit(2);
        }
        var url=System.getenv("RESTORE_DB_URL");
        var user=System.getenv("RESTORE_DB_USER");
        if(url==null||url.isBlank()||user==null||user.isBlank()){
            System.err.println("Set RESTORE_DB_URL and RESTORE_DB_USER for the restored database.");
            System.exit(2);
        }
        boolean apply=args[0].equals("--apply");
        try{
            var source=new DriverManagerDataSource(url,user,System.getenv("RESTORE_DB_PASSWORD"));
            int count=replay(source,apply);
            System.out.println((apply?"Removed restored homes marked as deleted: ":"Restored homes marked as deleted: ")+count);
            System.out.println((apply?"Removed restored former-home notes marked as deleted: ":
                "Restored former-home notes marked as deleted: ")+replayFormerNotes(source,apply));
        }catch(RuntimeException error){
            System.err.println("Deletion log replay failed. Check the restored database connection and schema.");
            System.exit(1);
        }
    }

    static int replayFormerNotes(DataSource source,boolean apply){
        var sql=new JdbcTemplate(source);
        var rows=FormerNoteService.restored(sql);
        if(!apply)return rows.size();
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        int count=0;
        for(var row:rows)
            if(Boolean.TRUE.equals(transaction.execute(status->FormerNoteService.removeRestored(sql,row))))count++;
        return count;
    }

    static int replay(DataSource source,boolean apply){
        var sql=new JdbcTemplate(source);
        var ids=sql.queryForList("""
            SELECT h.id FROM household h JOIN household_deletion_log d ON d.household_id=h.id ORDER BY h.id
            """,String.class);
        if(!apply)return ids.size();

        var transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        int count=0;
        for(String id:ids){
            Boolean removed=transaction.execute(status->{
                if(sql.queryForList("SELECT id FROM household WHERE id=? FOR UPDATE",String.class,id).isEmpty())
                    return false;
                HouseholdDeletionService.deleteRows(sql,id);
                return true;
            });
            if(Boolean.TRUE.equals(removed))count++;
        }
        return count;
    }

}
