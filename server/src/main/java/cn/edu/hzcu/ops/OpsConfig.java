package cn.edu.hzcu.ops;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

public record OpsConfig(Path controlRoot,Path runtimeRoot,Path artifactRoot,Path backupRoot,Path mediaRoot,
    Path javaExecutable,Path mysqlExecutable,Path dumpExecutable,Path businessEnvironment,int port,String healthUrl,String allowedOrigin,String databaseName) {
    static Path path(JsonNode n,String name){String value=n.path(name).asString("");if(value.isBlank())throw new IllegalArgumentException("OPS_CONFIG_MISSING");return Path.of(value).toAbsolutePath().normalize();}
    public static OpsConfig load(Path file)throws IOException {
        var n=OpsStore.JSON.readTree(Files.readString(file));
        var config=new OpsConfig(path(n,"controlRoot"),path(n,"runtimeRoot"),path(n,"artifactRoot"),path(n,"backupRoot"),path(n,"mediaRoot"),path(n,"javaExecutable"),path(n,"mysqlExecutable"),path(n,"dumpExecutable"),path(n,"businessEnvironment"),n.path("port").asInt(8091),n.path("healthUrl").asString("http://127.0.0.1:8080/actuator/health"),n.path("allowedOrigin").asString("http://127.0.0.1:5173"),n.path("databaseName").asString("campushub_dev"));
        config.validate();return config;
    }
    public void validate()throws IOException {
        if(port<1024||port>65535||!Set.of("campushub_dev","campushub_test").contains(databaseName))throw new IOException("OPS_CONFIG_INVALID");
        URI health=URI.create(healthUrl),origin=URI.create(allowedOrigin);
        if(!health.getScheme().equals("http")||!Set.of("127.0.0.1","localhost").contains(health.getHost())||health.getUserInfo()!=null||health.getQuery()!=null||health.getFragment()!=null||health.getPort()<1024||health.getPort()==port)throw new IOException("OPS_HEALTH_TARGET_INVALID");
        if(!Set.of("http","https").contains(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!origin.getPath().isEmpty())throw new IOException("OPS_ORIGIN_INVALID");
        var roots=List.of(controlRoot,runtimeRoot,artifactRoot,backupRoot,mediaRoot);
        for(int i=0;i<roots.size();i++)for(int j=i+1;j<roots.size();j++)if(roots.get(i).startsWith(roots.get(j))||roots.get(j).startsWith(roots.get(i)))throw new IOException("OPS_ROOTS_OVERLAP");
        for(Path p:roots){OpsStore.rejectLinks(p);Files.createDirectories(p);}
        for(Path p:List.of(javaExecutable,mysqlExecutable,dumpExecutable,businessEnvironment)){OpsStore.rejectLinks(p);if(!Files.isRegularFile(p))throw new IOException("OPS_CONFIG_FILE_MISSING");}
    }
    Map<String,String> environment()throws IOException {
        var n=OpsStore.JSON.readTree(Files.readString(businessEnvironment));var map=new HashMap<String,String>();
        n.properties().forEach(e->{if(e.getKey().startsWith("CAMPUSHUB_"))map.put(e.getKey(),e.getValue().asString());});
        if(!Set.of("127.0.0.1","localhost").contains(map.getOrDefault("CAMPUSHUB_DB_HOST","127.0.0.1"))||!databaseName.equals(map.getOrDefault("CAMPUSHUB_DB_NAME","campushub_dev")))throw new IOException("OPS_DATABASE_TARGET_INVALID");
        if(map.getOrDefault("CAMPUSHUB_DB_USERNAME","").isBlank()||map.getOrDefault("CAMPUSHUB_DB_PASSWORD","").isBlank())throw new IOException("OPS_DATABASE_CONFIG_MISSING");
        var parent=businessEnvironment.getParent();
        for(var item:Map.of("CAMPUSHUB_AUTH_PEPPER",".env.auth-key.txt","CAMPUSHUB_MAIL_PASSWORD",".env.smtp.txt").entrySet())if(Files.isRegularFile(parent.resolve(item.getValue())))map.put(item.getKey(),Files.readString(parent.resolve(item.getValue())).strip());
        map.put("CAMPUSHUB_MEDIA_ROOT",mediaRoot.toString());map.put("CAMPUSHUB_BIND_ADDRESS","127.0.0.1");
        map.put("CAMPUSHUB_LOG_ROOT",runtimeRoot.resolve(".local-data/logs").toString());
        map.put("CAMPUSHUB_PORT",""+URI.create(healthUrl).getPort());return map;
    }
}
