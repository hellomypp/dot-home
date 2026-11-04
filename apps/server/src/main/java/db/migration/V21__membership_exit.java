package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V21__membership_exit extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection db=context.getConnection();
        boolean mysql=db.getMetaData().getDatabaseProductName().equals("MySQL");
        String legacyIndex=legacyIndex(db,mysql);
        String legacyConstraint=mysql?legacyIndex:constraint(db,legacyIndex);
        String quote=db.getMetaData().getIdentifierQuoteString();
        String legacy=quote+legacyConstraint.replace(quote,quote+quote)+quote;

        try(var sql=db.createStatement()){
            sql.execute("ALTER TABLE membership ADD COLUMN ended_at TIMESTAMP(6)");
            sql.execute("ALTER TABLE membership ADD COLUMN exit_request_id VARCHAR(80)");
            sql.execute("ALTER TABLE membership ADD COLUMN membership_version BIGINT NOT NULL DEFAULT 0");
            sql.execute("ALTER TABLE invitation ADD COLUMN created_at TIMESTAMP(6)");
            sql.execute("UPDATE membership SET ended_at=CURRENT_TIMESTAMP WHERE active=FALSE");
            sql.execute("CREATE INDEX ix_member_account_house_exit ON membership(user_id,household_id,ended_at)");
            sql.execute("""
                ALTER TABLE membership ADD COLUMN active_user_id VARCHAR(36)
                GENERATED ALWAYS AS (CASE WHEN active THEN user_id ELSE NULL END)
                """);
            sql.execute("CREATE UNIQUE INDEX uq_membership_active_user ON membership(household_id,active_user_id)");
            sql.execute("ALTER TABLE membership DROP "+(mysql?"INDEX ":"CONSTRAINT ")+legacy);
        }
    }

    private static String legacyIndex(Connection db,boolean mysql) throws SQLException {
        var indexes=new LinkedHashMap<String,Set<String>>();
        try(var rows=db.getMetaData().getIndexInfo(db.getCatalog(),mysql?null:db.getSchema(),"membership",true,false)){
            while(rows.next()){
                String index=rows.getString("INDEX_NAME"),column=rows.getString("COLUMN_NAME");
                if(index!=null&&column!=null)
                    indexes.computeIfAbsent(index,k->new HashSet<>()).add(column.toLowerCase(Locale.ROOT));
            }
        }
        return indexes.entrySet().stream().filter(entry->entry.getValue().equals(Set.of("household_id","user_id")))
            .map(Map.Entry::getKey).findFirst().orElseThrow(()->new SQLException("Legacy membership uniqueness is missing"));
    }

    private static String constraint(Connection db,String index) throws SQLException {
        try(var query=db.prepareStatement("""
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA=? AND TABLE_NAME='membership' AND INDEX_NAME=? AND CONSTRAINT_TYPE='UNIQUE'
            """)){
            query.setString(1,db.getSchema());query.setString(2,index);
            try(var result=query.executeQuery()){
                if(result.next())return result.getString(1);
            }
        }
        throw new SQLException("Legacy membership constraint is missing");
    }
}
