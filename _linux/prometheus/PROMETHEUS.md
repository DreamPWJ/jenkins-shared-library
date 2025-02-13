### 安装Prometheus监控 https://prometheus.io/docs/prometheus/latest/installation/

#### 安装前确保prometheus的配置prometheus.yml文件放在挂载目录下

mkdir -p /my/prometheus/

docker run -d --restart=always -p 9090:9090 \
-v /my/prometheus/:/etc/prometheus/  \
--name prometheus prom/prometheus

#### 安装Grafana监控 默认用户和密码均为admin  https://grafana.com/docs/grafana/latest/installation/docker/

sudo docker run -d --restart=always -p 3000:3000 --name grafana grafana/grafana

#### Prometheus监控MySQL

- 参考文档: https://zhuanlan.zhihu.com/p/337715590
- 启动MySQL监控服务 重启后重新执行 使用systemd作为服务管理工具重启系统后才能自启动监控服务
- nohup /opt/node_exporter/node_exporter 
- nohup /opt/mysqld_exporter/mysqld_exporter --config.my-cnf=/opt/mysqld_exporter/.my.cnf 

#### Prometheus监控Nginx

- 参考文档: https://blog.51cto.com/cuiyingfeng/4371773

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

#### Spring Boot yaml配置文件设置 开放/actuator/** 监控路径可能导致内存数据安全漏洞

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

#### Spring Boot启动类Application追加
  /**
    * 集成prometheus监控
    */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> configurer(@Value("${spring.application.name}") String applicationName) {
    return (registry) -> registry.config().commonTags("application", applicationName);
    }

####  查看度量指标是否集成成功
  访问 http://localhost:8080/actuator/prometheus

- 首先在Grafana的Data Sources导入Prometheus数据源后, 再配置导入监控面板类型 一个Prometheus服务只需要配置一次 多应用共享
- import配置https://grafana.com/dashboards/4701 是 jvm使用面板 和 10280 是 Spring Boot面板

Prometheus监控 http://120.92.140.217:9090
Grafana监控 http://120.92.140.217:3000  默认用户和密码均为admin

#### 各种PrometheusAlert告警通知服务 开源的运维告警中心消息转发系统

- 参考项目：https://github.com/feiyu563/PrometheusAlert

  docker pull feiyu563/prometheus-alert:latest

  docker run -d --restart=always -p 9091:8080 \
  -v /my/prometheus-alert/:/conf/  \
  -e PA_LOGIN_USER=prometheusalert \
  -e PA_LOGIN_PASSWORD=prometheusalert \
  -e PA_TITLE=PrometheusAlert \
  -e PA_OPEN_FEISHU=1 \
  -e PA_OPEN_DINGDING=1 \
  -e PA_OPEN_WEIXIN=1 \
  --name prometheus-alert feiyu563/prometheus-alert:latest