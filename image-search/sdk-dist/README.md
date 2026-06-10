# pod-radar 图像搜索 Java SDK

给第三方只需要这个 jar：

```text
podradar-sdk-0.1.0.jar
```

## 环境

需要 JDK 8+。

```bash
export POD_RADAR_ENDPOINT="http://10.10.131.205"
export POD_RADAR_API_KEY="你的 API Key"
```

## 启动示例

在当前目录直接编译并运行 Demo：

```bash
javac -cp podradar-sdk-0.1.0.jar ImageSearchDemo.java

java -cp .:podradar-sdk-0.1.0.jar ImageSearchDemo file /path/to/query.jpg 24
java -cp .:podradar-sdk-0.1.0.jar ImageSearchDemo url https://example.com/query.jpg 24
java -cp .:podradar-sdk-0.1.0.jar ImageSearchDemo text 黑色男款短袖
```

`ImageSearchDemo.class` 是 `javac` 编译后生成的字节码文件，只是本机运行 Demo 的临时产物，不需要交付给第三方。

## 业务项目引用

### 方式一：直接引用 jar

```bash
javac -cp podradar-sdk-0.1.0.jar ImageSearchDemo.java
java -cp .:podradar-sdk-0.1.0.jar ImageSearchDemo file query.jpg 24
```

### 方式二：Maven 本地安装

如果业务项目使用 Maven，可以把这个 jar 装到本地仓库：

```bash
mvn install:install-file \
  -DgroupId=io.podradar \
  -DartifactId=podradar-sdk \
  -Dversion=0.1.0 \
  -Dpackaging=jar \
  -Dfile=podradar-sdk-0.1.0.jar
```

然后在业务项目 `pom.xml` 里加：

```xml
<dependency>
    <groupId>io.podradar</groupId>
    <artifactId>podradar-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 最小代码

```java
try (PodRadarClient client = PodRadarClient.builder()
        .endpoint(System.getenv("POD_RADAR_ENDPOINT"))
        .apiKey(System.getenv("POD_RADAR_API_KEY"))
        .build()) {
    SearchResponse r = client.search(SearchRequest.fromFile(new File("query.jpg"), 24));
    r.results().forEach(h ->
        System.out.printf("%d %.4f %s%n", h.imageId(), h.score(), h.full()));
}
```
