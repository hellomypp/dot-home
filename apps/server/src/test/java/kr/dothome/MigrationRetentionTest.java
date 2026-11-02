package kr.dothome;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MigrationRetentionTest {
    String url(){return "jdbc:h2:mem:migration-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";}
    String username(){return "sa";} String password(){return "";}
    Map<String,List<String>> rows(Connection connection)throws Exception{
        Map<String,List<String>> snapshot=new TreeMap<>();
        for(String table:List.of("app_user","household","membership","pet","invitation")){
            var values=new ArrayList<String>();
            try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT * FROM "+table+" ORDER BY id")){
                while(result.next()){var row=new ArrayList<String>();for(int i=1;i<=result.getMetaData().getColumnCount();i++)row.add(result.getString(i));values.add(row.toString());}
            }
            snapshot.put(table,values);
        }
        return snapshot;
    }
    @Test void existingV6DataAndPendingInvitationSurviveV7()throws Exception{
        String url=url();
        Flyway.configure().dataSource(url,username(),password()).target("6").load().migrate();
        try(var connection=DriverManager.getConnection(url,username(),password());var sql=connection.createStatement()){
            sql.executeUpdate("INSERT INTO app_user(id,provider,subject,name) VALUES('old-user','google','old-sub','기존 사용자')");
            sql.executeUpdate("INSERT INTO household(id,name,owner_id,planned_people,revision) VALUES('old-house','기존 집','old-user',1,5)");
            sql.executeUpdate("INSERT INTO membership(id,household_id,user_id,name,appearance,role,place,mood,food,thought,x,y,action,active) VALUES('old-member','old-house','old-user','기존 이름','v1:3212','OWNER','home','','','',320,288,'idle',true)");
            sql.executeUpdate("INSERT INTO pet(id,household_id,name,kind,appearance) VALUES('old-pet','old-house','기존 반려동물','cat','legacy-color')");
            sql.executeUpdate("INSERT INTO invitation(id,household_id,token_hash,expires_at,cancelled) VALUES('old-invite','old-house','"+"a".repeat(64)+"','2030-01-01 00:00:00',false)");
            var before=rows(connection);
            Flyway.configure().dataSource(url,username(),password()).load().migrate();
            assertEquals(before,rows(connection));
            sql.executeUpdate("INSERT INTO household(id,name,owner_id,revision) VALUES('new-house','새 집','old-user',1)");
            try(var result=sql.executeQuery("SELECT planned_people FROM household WHERE id='new-house'")){assertTrue(result.next());assertEquals(1,result.getInt(1));}
        }
    }
}
