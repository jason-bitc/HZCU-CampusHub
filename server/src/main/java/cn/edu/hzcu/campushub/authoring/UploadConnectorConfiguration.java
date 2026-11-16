package cn.edu.hzcu.campushub.authoring;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
class UploadConnectorConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> deferUploadContinue() {
        // Allow authenticated header/length checks before inviting a large body.
        return factory -> factory.addConnectorCustomizers(connector -> connector.setProperty("continueResponseTiming", "onRead"));
    }
}
