package cn.edu.hzcu.campushub.community;

import java.time.Instant;
import java.util.*;

public final class CommunityModels {
    private CommunityModels(){}
    public record Receipt(long caseId,long revision,String state){}
    public record Page(List<Map<String,Object>> items,long total,int page,int size,long snapshot){}
    public record CaseAudit(long caseId, String state, long revision, String targetType, Long articleId, Long commentId,
                            List<AuditEvent> items, long total, int page, int size) {}
    public record AuditEvent(long sequence, String source, String action, String actorDomain, long actorId,
                             String targetType, long targetId, String contributionEventId, Long caseRevision, long revision,
                             String beforeState, String afterState, String outcome, String publicReply, String reason,
                             Instant occurredAt) {}
    record Target(Long article,Long comment,Long version){}
    record Case(long id,long user,String kind,String category,String targetType,Long article,Long comment,Long version,String description,String state,long revision,Long assignee,String digest,Instant created,Instant updated){}
    record Image(long sequence,String id,long user,Long caseId,String digest,String mime,long size,int width,int height){
        public Map<String,Object> view(){return Map.of("id",id,"mime",mime,"sizeBytes",size,"width",width,"height",height);}
    }
    record Previous(String digest,Receipt receipt){}
}
