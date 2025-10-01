# WinterBoot

WinterBoot는 Spring Boot의 핵심 아이디어(IoC 컨테이너, MVC 디스패처, 자동 구성)를 학습하기 위해 만든 경량 자바 프레임워크입니다. `com.sun.net.httpserver.HttpServer` 위에서 동작하면서 애노테이션 기반의 컨트롤러 매핑과 DI 컨테이너, 조건부 자동 구성을 제공합니다.

## 주요 기능

- **애노테이션 기반 IoC 컨테이너** – `@Component`, `@Inject` 등을 통해 빈을 생성하고 주입합니다.
- **경량 MVC 디스패처** – `@GetMapping`, `@PostMapping`으로 등록된 컨트롤러 메서드를 라우팅하고, 요청 본문을 `ObjectMapper`로 역직렬화합니다.
- **조건부 자동 구성** – `ServiceLoader`와 `@ConditionalOnClass`, `@ConditionalOnProperty`를 이용해 필요한 빈을 자동 등록합니다.
- **환경 프로퍼티 지원** – `application.properties`를 읽어 포트와 기능 토글을 제어합니다.

## 핵심 로직 요약

### 1. IoC 컨테이너와 DI 파이프라인

WinterBoot의 엔트리 포인트는 `ApplicationContext`이며, 컨텍스트는 패키지를 스캔하여 빈을 만들고, 그 후 의존성을 주입합니다.

```java
// ApplicationContext 생성 흐름
Set<Class<?>> componentClasses = new PackageScanner().scanComponents(basePackage);
componentClasses.forEach(this::createBean);
beans.values().forEach(this::injectDependencies);
```

- **클래스 스캐닝** – `PackageScanner`는 `Files.walk()`를 이용해 `.class` 파일을 순회하고 `@Component` 메타 애노테이션이 붙은 클래스를 선별합니다. 내부 클래스와 추상 타입은 필터링하여 실제 인스턴스화 가능한 클래스만 수집합니다.
- **빈 생성** – `createBean`은 기본 생성자를 호출하여 인스턴스를 만들고 `beans` 맵에 저장합니다. 모든 빈을 먼저 생성한 뒤에 `injectDependencies`를 호출하여 **순환 의존성 문제를 예방**했습니다.
- **필드 주입** – `@Inject`가 붙은 필드를 찾아 `resolveByAssignableType`으로 타입에 맞는 빈을 검색합니다. 동일한 타입의 후보가 둘 이상이면 예외를 던져 명시적 설계를 강제합니다.

```java
private Object resolveByAssignableType(Class<?> dependencyType) {
    Object exact = beans.get(dependencyType);
    if (exact != null) return exact;

    Object candidate = null;
    for (var entry : beans.entrySet()) {
        if (dependencyType.isAssignableFrom(entry.getKey())) {
            if (candidate != null) throw new RuntimeException("주입 후보가 둘 이상입니다");
            candidate = entry.getValue();
        }
    }
    return candidate;
}
```

### 2. PackageScanning 성능 개선

초기 구현은 BFS/DFS로 클래스 파일을 탐색했으나, NIO의 `Files.walk()`로 교체하면서 시스템 콜 수를 줄이고 스트림 기반 필터링을 활용했습니다. 동일 패키지를 10,000회 스캔한 실험에서 `Files.walk()` 버전이 BFS 대비 평균 **약 37% 빠른** 결과를 보였습니다.

### 3. MVC 디스패처와 라우팅

`DispatcherHandler`는 `HttpHandler`를 구현하여 `HttpExchange` 요청을 직접 처리합니다. 컨텍스트의 모든 빈을 순회하여 컨트롤러 매핑 정보를 메모리에 준비합니다.

```java
// DispatcherHandler.initHandlerMapping()
handlerMapping
    .computeIfAbsent(path, k -> new HashMap<>())
    .put(httpMethod, new MethodInfo(bean, method, isRestController, path));
```

- **요청 매핑** – 요청이 들어오면 `findMethodInfo`가 패턴 매칭(`{id}` 형태의 경로 변수 지원)을 수행하고, 매개변수 애노테이션에 따라 경로·쿼리·본문 데이터를 파싱합니다.
- **데이터 바인딩** – `ObjectMapper`를 이용한 JSON 역직렬화(`@RequestBody`)와 기본 타입, 배열, `List<T>` 변환을 지원합니다.
- **응답 처리** – `@RestController` 여부에 따라 JSON 또는 텍스트 응답을 전송합니다.

### 4. 자동 구성(Autoconfiguration)

자동 구성은 `ServiceLoader` 기반으로 확장되며, 각 구성 클래스가 `AutoConfiguration` 인터페이스를 구현합니다.

```java
for (AutoConfiguration ac : ServiceLoader.load(AutoConfiguration.class, cl)) {
    if (!ConditionEvaluator.matchesConditionalOnClass(...)) continue;
    if (!ConditionEvaluator.matchesConditionalOnProperty(...)) continue;
    ac.apply(ctx, env);
}
```

- `WebServerAutoConfiguration`은 `@ConditionalOnProperty(server.enabled=true)`가 만족될 때 `HttpServer`와 `DispatcherHandler`를 등록하고 서버를 시작합니다.
- `JacksonAutoConfiguration`은 `ObjectMapper` 빈을 주입해 컨트롤러에서 JSON 직렬화를 사용할 수 있도록 합니다.

### 5. 도메인 샘플 구성요소

- `UserRepository` → 메모리 리스트 기반 CRUD 저장소
- `UserService` → 저장소를 감싸며 비즈니스 로직을 제공 (`@Inject`로 저장소 주입)
- `UserController` → REST API 엔드포인트 예제 (`@GetMapping`, `@PostMapping`)

## 아키텍처 개요

### IoC & DI 컨테이너

<img width="3840" height="3624" alt="IOC_DI" src="https://github.com/user-attachments/assets/516a225e-9b8d-4f66-8979-2dd1336d1dbd" />

- `PackageScanner`가 컴포넌트 후보를 읽고 빈으로 생성합니다.
- 모든 빈 생성 이후 한 번에 의존성을 주입하여 순환 의존성을 방지합니다.

### MVC 요청 처리

<img width="3840" height="2698" alt="MVC-2" src="https://github.com/user-attachments/assets/dbf8e802-aee6-4057-8032-1ece3ed243cf" />


- 경로 변수, 쿼리 파라미터, `@RequestBody`를 모두 지원합니다.
- `HttpExchange` 객체를 직접 주입받을 수도 있습니다.

### 자동 구성 파이프라인

<img width="3840" height="2212" alt="AUTO-CONFIGURATION" src="https://github.com/user-attachments/assets/7ceab837-cd37-490e-b4d3-89fa323239a4" />

- `server.enabled=false`로 설정하면 웹 서버 자동 구성을 비활성화할 수 있습니다.

## 성능 및 벤치마크

| 시나리오 | WinterBoot | 비교 대상 | 결과 |
| --- | --- | --- | --- |
| **PackageScanning** | `Files.walk()` 기반 스캐닝 | DFS/BFS 기반 실험 | 약 **37% 속도 개선** |

<img width="1095" height="252" alt="2" src="https://github.com/user-attachments/assets/0ab1abc1-c7e4-45b0-afab-0a1370d5c431" />

| **POST 50,000건 처리** | **14.5초** | Spring Boot **19초** | WinterBoot가 약 24% 빠름 |

<img width="755" height="94" alt="3" src="https://github.com/user-attachments/assets/7dde3695-70bf-4a92-a6f6-a722c9d47ad4" />

- `Files.walk()` 기반 스캐너가 내부 클래스, 추상 타입을 필터링하여 불필요한 클래스 로딩을 줄입니다.
- 컨트롤러와 매핑을 메모리 캐시에 올려 반복 요청 처리 속도를 높였습니다.
- **SpringBoot VS WinterBoot** – 동일 하드웨어에서 POST 요청 50,000건을 처리했을 때 Spring Boot가 19초, WinterBoot가 14.5초로 측정되어 WinterBoot가 약 24% 빠르게 응답합니다.

## 실행 방법

```bash
./gradlew build
./gradlew run
```

애플리케이션이 시작되면 8081 포트(기본값)에서 HTTP 서버가 동작합니다. 포트는 `src/main/resources/application.properties`의 `server.port`로 변경할 수 있습니다.

### 예시 요청

```bash
# 단일 유저 조회
curl http://localhost:8081/users/1

# 이름이 winter를 포함하는 유저 검색
curl "http://localhost:8081/users?name=winter&limit=5"

# JSON 본문으로 유저 등록
curl -X POST http://localhost:8081/users \
     -H "Content-Type: application/json" \
     -d '{"id":1,"name":"Winter"}'
```

## 프로젝트 구조

```
src/main/java/com/winter/winterboot
├── annotation/      # DI 및 MVC 애노테이션
├── autoconf/        # Jackson, HttpServer 자동 구성
├── component/       # 샘플 빈(UserRepository 등)
├── controller/      # 예제 RestController
├── core/            # ApplicationContext, 조건 평가, 스캐너
└── WinterBootApplication.java
```

## 트러블슈팅 기록

| 상황 | 원인 | 해결 |
| --- | --- | --- |
| `@Inject` 즉시 실행 시 순환 의존성 발생 | 빈을 생성하는 즉시 주입하면서 상호 참조가 초기화되지 않음 | **모든 빈을 먼저 생성 → 전체 주입을 한 번에 실행** |
| `Class.forName()`이 클래스 초기화를 수행 | 초기화가 필요 없지만 기본 호출이 로딩+초기화를 수행 | `Class.forName(name, false, cl)`로 변경해 **로딩만 수행** |

## 참고 이미지

- IoC/DI, MVC, Auto-Configuration 아키텍처 다이어그램은 제공된 자료를 참고했습니다.

## 향후 개선 아이디어

- AOP, 인터셉터 등 부가 기능 추가
- 필터 체인 기반 미들웨어 설계
- 빌트인 톰캣/네티 어댑터 지원
