package cn.edu.hzcu.campushub.tools;

import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.common.DatabaseConfig;
import cn.edu.hzcu.campushub.community.CommunityInput;
import java.io.*;
import java.sql.*;
import java.time.*;
import javax.sql.DataSource;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Explicit local setup. No default account, public registration or application startup hook. */
public final class InitializeCommunityAdmin {
    private InitializeCommunityAdmin(){}
    public static long save(DataSource source,PasswordEncoder encoder,Clock clock,String login,String password)throws SQLException{
        String name=CommunityInput.login(login),hash=encoder.encode(AuthInput.password(password));Timestamp now=Timestamp.from(clock.instant());
        try(var connection=source.getConnection()){
            connection.setAutoCommit(false);try{
                // Same gate -> account order as login, including a concurrent local reset.
                try(var q=connection.prepareStatement("DELETE FROM community_admin_login_gate WHERE name_digest=?")){q.setString(1,AuthCrypto.tokenDigest(name));q.executeUpdate();}
                long id;try(var q=connection.prepareStatement("INSERT INTO community_admin(login_name,password_hash,enabled,created_at) VALUES(?,?,TRUE,?) ON DUPLICATE KEY UPDATE password_hash=?,enabled=TRUE",Statement.RETURN_GENERATED_KEYS)){q.setString(1,name);q.setString(2,hash);q.setTimestamp(3,now);q.setString(4,hash);q.executeUpdate();}
                try(var q=connection.prepareStatement("SELECT id FROM community_admin WHERE login_name=? FOR UPDATE")){q.setString(1,name);try(var rows=q.executeQuery()){rows.next();id=rows.getLong(1);}}
                try(var q=connection.prepareStatement("UPDATE community_admin_session SET revoked_at=? WHERE admin_id=? AND revoked_at IS NULL")){q.setTimestamp(1,now);q.setLong(2,id);q.executeUpdate();}
                connection.commit();return id;
            }catch(Exception failure){connection.rollback();throw failure;}
        }
    }
    public static void main(String[] args){
        try{
            if(args.length!=0)throw new IllegalArgumentException();String host=System.getenv().getOrDefault("CAMPUSHUB_DB_HOST","127.0.0.1");if(!host.equals("127.0.0.1")&&!host.equals("localhost"))throw new IllegalArgumentException();
            var settings=new DatabaseConfig.Settings();settings.setHost(host);settings.setPort(Integer.parseInt(System.getenv().getOrDefault("CAMPUSHUB_DB_PORT","3306")));settings.setName(System.getenv().getOrDefault("CAMPUSHUB_DB_NAME","campushub_dev"));settings.setUsername(System.getenv("CAMPUSHUB_DB_USERNAME"));settings.setPassword(System.getenv("CAMPUSHUB_DB_PASSWORD"));settings.validate();
            var input=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8));String login=input.readLine(),password=input.readLine(),confirm=input.readLine();if(password==null||!password.equals(confirm))throw new IllegalArgumentException();
            var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:mysql://"+host+":"+settings.getPort()+"/"+settings.getName()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=5000",settings.getUsername(),settings.getPassword());
            long id=save(source,AuthConfiguration.passwordEncoder(),Clock.systemUTC(),login,password);System.out.println("LOCAL_SETUP community admin credentials saved; adminId="+id+"; prior sessions revoked.");
        }catch(Exception failure){System.err.println("ADMIN_LOCAL_SETUP_FAILED; check local configuration, migrated database and input.");System.exit(1);}
    }
}
