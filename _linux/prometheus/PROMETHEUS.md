#### 安装Prometheus监控 https://prometheus.io/docs/prometheus/latest/installation/

docker run -d --restart=always -p 9090:9090 \
-v /my/prometheus/:/etc/prometheus/  \
--name prometheus  prom/prometheus


#### 安装Grafana监控 默认用户和密码均为admin  https://grafana.com/docs/grafana/latest/installation/docker/

sudo docker run  -d --restart=always -p 3000:3000 --name grafana grafana/grafana

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

在Grafana导入Prometheus数据源后, 配置导入监控面板
- import配置https://grafana.com/dashboards/4701 是 jvm使用面板 和 10280 是 Spring Boot面板

Prometheus监控 http://118.190.150.96:9090
Grafana监控 http://118.190.150.96:3000  默认用户和密码均为admin


###  钉钉告警通知
#### 参考文章 
- https://yunlzheng.gitbook.io/prometheus-book/parti-prometheus-ji-chu/alert/alert-manager-use-receiver/alert-manager-extension-with-webhook
- https://blog.51cto.com/lovebetterworld/2839894

docker run -d --restart=always --name alertmanager -p 9093:9093  \
-v /my/prometheus/alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml prom/alertmanager:latest

####  prometheus-webhook-dingtalk 2.0版本 弃用 --ding.profile等
docker pull timonwong/prometheus-webhook-dingtalk
docker run -d --restart=always -p 8060:8060 --name prometheus-webhook-dingtalk timonwong/prometheus-webhook-dingtalk \
 --ding.profile="webhook1=https://oapi.dingtalk.com/robot/send?access_token=7e0a34d57be41808ab02b1955ed2f19d64d1fbd95e521331eff8cfe16e05b861"
