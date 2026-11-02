package kr.dothome;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** Validated before DataSource/Flyway construction, including accidental image profile overrides. */
@Configuration
class ProductionConfiguration {
    @Bean static BeanFactoryPostProcessor productionSettings(Environment env) {
        return factory -> { if (env.matchesProfiles("prod") || "beta".equals(env.getProperty("DOT_HOME_DEPLOYMENT"))) validate(env); };
    }
    static String required(Environment env,String key) {
        String value=env.getProperty(key);
        if(value==null||value.isBlank())throw new IllegalStateException("Missing production setting: "+key);
        return value;
    }
    static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
    static void validate(Environment env) {
        check(env.matchesProfiles("prod")&&env.matchesProfiles("google")&&!env.matchesProfiles("dev","local","test","kakao"),"Production requires google,prod without development/local/test profiles");
        for(String key:new String[]{"DB_URL","DB_USER","DB_PASSWORD","GOOGLE_CLIENT_ID","GOOGLE_CLIENT_SECRET","APP_ORIGIN","PORT"})required(env,key);
        String db=required(env,"DB_URL");
        boolean validDb=false;
        try{
            URI uri=URI.create(db.substring(5));
            validDb=db.startsWith("jdbc:mysql://")&&"mysql".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null&&uri.getPath()!=null&&uri.getPath().length()>1;
            String query=uri.getQuery();
            if(query!=null)for(String part:query.split("&")){String key=part.split("=",2)[0].toLowerCase(Locale.ROOT);if(java.util.Set.of("user","username","password","token","accesstoken").contains(key))validDb=false;}
        }catch(Exception ignored){}
        check(validDb,"DB_URL must be a MySQL JDBC URL with a database; credentials belong in separate variables");
        check(db.equals(env.getProperty("spring.datasource.url")),"Production datasource must use DB_URL");
        check(required(env,"DB_USER").equals(env.getProperty("spring.datasource.username"))&&required(env,"DB_PASSWORD").equals(env.getProperty("spring.datasource.password")),"Production datasource must use DB_USER and DB_PASSWORD");
        String origin=required(env,"APP_ORIGIN");boolean validOrigin=false;
        try {URI uri=URI.create(origin);validOrigin="https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null&&(uri.getPath()==null||uri.getPath().isEmpty())&&!java.util.Set.of("localhost","127.0.0.1","[::1]","::1").contains(uri.getHost());}catch(Exception ignored){}
        check(validOrigin,"APP_ORIGIN must be an external HTTPS origin without a path, query or fragment");
        check(origin.equals(env.getProperty("app.origin")),"app.origin must equal APP_ORIGIN");
        check((origin+"/login/oauth2/code/google").equals(env.getProperty("spring.security.oauth2.client.registration.google.redirect-uri")),"Google callback must use APP_ORIGIN");
        int port=0;try{port=Integer.parseInt(required(env,"PORT"));}catch(NumberFormatException ignored){}
        check(port>0&&port<=65535&&String.valueOf(port).equals(env.getProperty("server.port")),"server.port must match valid PORT");
        check("0.0.0.0".equals(env.getProperty("server.address")),"Production must listen on 0.0.0.0");
        check("com.mysql.cj.jdbc.Driver".equals(env.getProperty("spring.datasource.driver-class-name")),"Production requires the MySQL driver");
        check("validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))&&env.getProperty("spring.flyway.enabled",Boolean.class,false),"Production requires Flyway and schema validation");
        check(env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false)&&env.getProperty("server.servlet.session.cookie.http-only",Boolean.class,false)&&"lax".equalsIgnoreCase(env.getProperty("server.servlet.session.cookie.same-site")),"Production session cookies must be Secure, HttpOnly and SameSite=Lax");
        check(!env.getProperty("server.tomcat.accesslog.enabled",Boolean.class,false),"Token-bearing access logs must remain disabled");
        for(String logger:new String[]{"root","org.springframework.web","org.springframework.security","org.hibernate.SQL","com.zaxxer.hikari"}){
            String level=env.getProperty("logging.level."+logger,"INFO");check(!level.equalsIgnoreCase("DEBUG")&&!level.equalsIgnoreCase("TRACE"),"Production debug/trace logging is not allowed");
        }
    }
}
