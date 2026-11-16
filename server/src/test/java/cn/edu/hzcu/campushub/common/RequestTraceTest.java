package cn.edu.hzcu.campushub.common;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class RequestTraceTest {
    @AfterEach void clean(){RequestTrace.clear();}
    @Test void methodsHaveDistinctFiniteActionsWithoutPayloads(){
        assertThat(RequestTrace.action("/api/v1/auth/login","POST")).isEqualTo("AUTH_LOGIN");
        assertThat(RequestTrace.action("/api/v1/auth/register","POST")).isEqualTo("AUTH_REGISTER");
        assertThat(RequestTrace.action("/api/v1/me/articles/42/draft","GET")).isEqualTo("DRAFT_READ");
        assertThat(RequestTrace.action("/api/v1/me/articles/42/draft","PUT")).isEqualTo("DRAFT_SAVE");
        assertThat(RequestTrace.action("/api/v1/me/contact-settings","PUT")).isEqualTo("CONTACT_UPDATE");
        assertThat(RequestTrace.module("AUTH_LOGIN")).isEqualTo("AUTH");
        assertThat(RequestTrace.module("ARTICLE_IMAGE_UPLOAD")).isEqualTo("AUTHORING");
        assertThat(RequestTrace.action("/api/v1/articles/42/like","PUT")).isEqualTo("LIKE_SET");
        assertThat(RequestTrace.action("/api/v1/articles/42/like","DELETE")).isEqualTo("LIKE_CANCEL");
        assertThat(RequestTrace.action("/api/v1/articles/42/favorite","PUT")).isEqualTo("FAVORITE_SET");
        assertThat(RequestTrace.action("/api/v1/articles/42/favorite","DELETE")).isEqualTo("FAVORITE_CANCEL");
        assertThat(RequestTrace.action("/api/v1/articles/42/interaction-state","GET")).isEqualTo("INTERACTION_READ");
        assertThat(RequestTrace.action("/api/v1/me/favorites","GET")).isEqualTo("FAVORITES_LIST");
        assertThat(RequestTrace.module("FAVORITES_LIST")).isEqualTo("INTERACTION");
    }
    @Test void reusedWorkerClearsIdsErrorsAndCausesOnSuccessAndException()throws Exception{
        try(var worker=Executors.newSingleThreadExecutor()){
            worker.submit(()->{
                var filter=new RequestTrace();var request=new MockHttpServletRequest("GET","/api/v1/articles/42");request.setServletPath("/api/v1/articles/42");
                assertThatThrownBy(()->filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{RequestTrace.article(42);RequestTrace.actor(7);RequestTrace.cause(new IllegalStateException("private"));RequestTrace.errorCode("PRIVATE_TEST");throw new IllegalStateException("controlled");})).isInstanceOf(IllegalStateException.class);
                assertThat(MDC.get("traceId")).isNull();assertThat(MDC.get("errorCode")).isNull();assertThat(RequestTrace.context().articleId()).isNull();var next=new IllegalStateException();assertThat(RequestTrace.causeOr(next)).isSameAs(next);
                var second=new MockHttpServletRequest("GET","/api/v1/catalog");second.setServletPath("/api/v1/catalog");
                try{filter.doFilter(second,new MockHttpServletResponse(),(req,res)->assertThat(RequestTrace.context().actorId()).isNull());}catch(Exception e){throw new AssertionError(e);}
                assertThat(MDC.get("action")).isNull();assertThat(RequestTrace.context().articleId()).isNull();
            }).get();
        }
    }
    @Test void delayedSnapshotRestoresReceivingThreadsOwnContext()throws Exception{
        String origin=UUID.randomUUID().toString();MDC.put("traceId",origin);MDC.put("action","DRAFT_SAVE");RequestTrace.article(42);var captured=RequestTrace.snapshot();RequestTrace.clear();
        try(var worker=Executors.newSingleThreadExecutor()){
            worker.submit(()->{
                String receiving=UUID.randomUUID().toString();MDC.put("traceId",receiving);MDC.put("action","COMMENT_LIST");RequestTrace.actor(9);
                try{
                    RequestTrace.with(captured,()->{assertThat(RequestTrace.current()).isEqualTo(origin);assertThat(RequestTrace.context().articleId()).isEqualTo(42);assertThat(MDC.get("action")).isEqualTo("DRAFT_SAVE");});
                    assertThat(RequestTrace.current()).isEqualTo(receiving);assertThat(RequestTrace.context().actorId()).isEqualTo(9);assertThat(RequestTrace.context().articleId()).isNull();
                }finally{RequestTrace.clear();}
                assertThat(RequestTrace.current()).isNull();
            }).get();
        }
    }
}
