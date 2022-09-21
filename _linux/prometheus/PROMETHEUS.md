#### 安装Prometheus监控 https://prometheus.io/docs/prometheus/latest/installation/

##### 安装前确保prometheus的配置yaml文件放在挂载目录下

docker run -d --restart=always -p 9090:9090 \
-v /my/prometheus/:/etc/prometheus/  \
--name prometheus prom/prometheus

#### 安装Grafana监控 默认用户和密码均为admin  https://grafana.com/docs/grafana/latest/installation/docker/

sudo docker run -d --restart=always -p 3000:3000 --name grafana grafana/grafana

#### Spring Boot集成Prometheus监控

- 添加依赖

    <!-- Spring Boot监控 -->
      <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
      </dependency>

  <!-- Prometheus监控  https://prometheus.io/docs/introduction/overview/ -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
            <version>1.7.5</version>
        </dependency>

- yaml配置文件

management:
  endpoint:
    health:
      show-details: always
    shutdown:
      enabled: true
    prometheus:
      enabled: true
  endpoints:
    web:
      exposure:
        include: "*"
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}

- Spring Boot启动类Application追加
  /**
  * 集成prometheus监控
    */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> configurer(@Value("${spring.application.name}") String applicationName) {
    return (registry) -> registry.config().commonTags("application", applicationName);
    }

- 查看度量指标是否集成成功
  访问 http://localhost:8080/actuator/prometheus

首先在Grafana的Data Sources导入Prometheus数据源后, 再配置导入监控面板类型

- import配置https://grafana.com/dashboards/4701 是 jvm使用面板 和 10280 是 Spring Boot面板

Prometheus监控 http://118.190.150.96:9090
Grafana监控 http://118.190.150.96:3000  默认用户和密码均为admin

### 各种PrometheusAlert告警通知服务

- 参考项目：https://github.com/feiyu563/PrometheusAlert