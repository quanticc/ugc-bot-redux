package com.ugcleague.ops.config;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.xml.xpath.Jaxp13XPathTemplate;
import org.springframework.xml.xpath.XPathOperations;

@Configuration
public class RestConfiguration {

//    @Bean(name = "restTemplate")
//    @Autowired
//    public RestOperations restTemplate(Jaxb2Marshaller jaxb2Marshaller) throws Exception {
//        final MarshallingHttpMessageConverter converter = new MarshallingHttpMessageConverter();
//        converter.setMarshaller(jaxb2Marshaller);
//        converter.setUnmarshaller(jaxb2Marshaller);
//        final List<HttpMessageConverter<?>> converterList = new ArrayList<>();
//        converterList.add(converter);
//        RestTemplate restTemplate = new RestTemplate();
//        restTemplate.setMessageConverters(converterList);
//        return restTemplate;
//    }

    @Bean(name = "restTemplate")
    @Autowired
    public RestOperations restTemplate() {
        return new RestTemplate();
    }

    @Bean(name = "xPathTemplate")
    public XPathOperations xPathTemplate() {
        return new Jaxp13XPathTemplate();
    }

//    @Bean
//    public Jaxb2Marshaller jaxb2Marshaller() throws Exception {
//        final Jaxb2Marshaller jaxb2Marshaller = new Jaxb2Marshaller();
//        jaxb2Marshaller.setClassesToBeBound(People.class);
//        jaxb2Marshaller.setSchema(new ClassPathResource("people-schema.xsd"));
//        return jaxb2Marshaller;
//    }

    @Bean
    @Autowired
    public ClientHttpRequestFactory httpRequestFactory(HttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        return requestFactory;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClients.createDefault();
    }
}
