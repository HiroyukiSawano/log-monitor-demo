package com.example.demo.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * Elasticsearch 新版 Java 客户端配置
 * 包含跳过 HTTPS 证书验证的逻辑，专门用于测试环境无正规证书的 ES 集群。
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    // 可选的账号密码配置，如果在测试环境 ES 没有配置密码，这里保持空
    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Bean
    public ElasticsearchClient elasticsearchClient()
            throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {

        // 1. 构建能够信任所有证书的 SSLContext (跳过 SSL 证书校验)
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(new TrustAllStrategy())
                .build();

        // 2. 账号密码凭证提供者
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        if (username != null && !username.isEmpty()) {
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        }

        // 3. 构建底层的 RestClient，必须指明 scheme="https" 并注入免校验的 SSL 上下文
        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(host, port, "https"))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE) // 禁用主机名验证
                        .setDefaultCredentialsProvider(credentialsProvider));

        RestClient restClient = restClientBuilder.build();

        // 4. 使用 Jackson 进行 JSON 与对象的转换映射
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        // 5. 创建并返回强类型的新版客户端
        return new ElasticsearchClient(transport);
    }
}
