
package br.edu.ufcspa.interop.ambulatorio.fhir.config;

import java.util.List;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.openapi.OpenApiInterceptor;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;

@Configuration
public class FhirConfig {

    // LACUNA 1: Contexto FHIR R4 reutilizavel
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4Cached();
    }

    @Bean
    public RestfulServer restfulServer(
            FhirContext fhirContext,
            List<IResourceProvider> providers) {

        RestfulServer server = new RestfulServer(fhirContext);

        server.setServerName("Servidor FHIR (UFCSPA)");
        server.setServerVersion("1.0.0");

        // LACUNA 3: Registro dos providers
        server.setResourceProviders(providers);

        // Respostas em JSON
        server.setDefaultResponseEncoding(EncodingEnum.JSON);

        // LACUNA 4: Respostas indentadas
        server.setDefaultPrettyPrint(true);

        server.registerInterceptor(new ResponseHighlighterInterceptor());
        server.registerInterceptor(new OpenApiInterceptor());

        // LACUNA 5: Configuracao CORS
        CorsConfiguration cors = new CorsConfiguration();

        cors.addAllowedOrigin("*");
        cors.addAllowedHeader("*");
        cors.setAllowedMethods(
                List.of("GET", "POST", "PUT", "OPTIONS")
        );
        cors.addExposedHeader("Location");
        cors.addExposedHeader("Content-Location");

        server.registerInterceptor(new CorsInterceptor(cors));

        return server;
    }

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServlet(
            RestfulServer server) {

        // LACUNA 2: Servidor FHIR em /fhir
        ServletRegistrationBean<RestfulServer> registro =
                new ServletRegistrationBean<>(server);

        registro.setName("FhirServlet");
        registro.addUrlMappings("/fhir/*");
        registro.setLoadOnStartup(1);

        return registro;
    }
}
