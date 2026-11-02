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
            Flyway.configure().dataSource(url,username(),password()).target("7").load().migrate();
            assertEquals(before,rows(connection));
            sql.executeUpdate("INSERT INTO household(id,name,owner_id,revision) VALUES('new-house','새 집','old-user',1)");
            try(var result=sql.executeQuery("SELECT planned_people FROM household WHERE id='new-house'")){assertTrue(result.next());assertEquals(1,result.getInt(1));}
        }
    }
    @Test void v8AddsDesignWithoutChangingLegacyRecords()throws Exception{
        String url=url();
        Flyway.configure().dataSource(url,username(),password()).target("7").load().migrate();
        try(var connection=DriverManager.getConnection(url,username(),password());var sql=connection.createStatement()){
            sql.executeUpdate("INSERT INTO app_user(id,provider,subject,name) VALUES('v8-user','google','v8-sub','기존 계정')");
            sql.executeUpdate("INSERT INTO household(id,name,owner_id,revision) VALUES('v8-house','기존 집','v8-user',8)");
            sql.executeUpdate("INSERT INTO membership(id,household_id,user_id,name,appearance,role,place,mood,food,thought,x,y,action,active) VALUES('v8-member','v8-house','v8-user','기존 이름','v1:3212','OWNER','home','','','',320,288,'idle',true)");
            sql.executeUpdate("INSERT INTO pet(id,household_id,name,kind,appearance) VALUES('v8-pet','v8-house','모카','cat','cream')");
            Flyway.configure().dataSource(url,username(),password()).load().migrate();
            try(var r=sql.executeQuery("SELECT name,revision,preset_id,design_version,object_variants FROM household WHERE id='v8-house'")){assertTrue(r.next());assertEquals("기존 집",r.getString(1));assertEquals(8,r.getLong(2));assertEquals("classic_home",r.getString(3));assertEquals(0,r.getLong(4));assertEquals("{}",r.getString(5));}
            try(var r=sql.executeQuery("SELECT appearance,x,y,avatar_space FROM membership WHERE id='v8-member'")){assertTrue(r.next());assertEquals("v1:3212",r.getString(1));assertEquals(320,r.getDouble(2));assertEquals(288,r.getDouble(3));assertEquals("legacy",r.getString(4));}
            try(var r=sql.executeQuery("SELECT name FROM pet WHERE id='v8-pet'")){assertTrue(r.next());assertEquals("모카",r.getString(1));}
        }
    }

}
