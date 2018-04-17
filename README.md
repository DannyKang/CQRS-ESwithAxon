# CQRS-ESwithAxon

본 Hans-On Lab에서는 Axon Framework를 활용해서 CQRS와 Event Sourcing에 대해서 실습한다.




## Axon Framework
Axon Framework은 Event-Driven lightweight CQRS framework으로 Aggregate의 상태정보를 저장하는 방법과 EventSourcing을 지원한다.


### Axon Framework Architecture

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)


### Axon Framework Building Block
예기에 각 Building Block 설명 추가
 - Command
 - CommandBus
 - Event
 - EventBus




Axon framework을 이용하기 위해서는 Maven dependency를 추가하면 된다.
```
<dependency>
  <groupId>org.axonframework</groupId>
  <artifactId>axon-core</artifactId>
  <version>${axon.version}</version>
</dependency>

```


 - core Axon's core code
 - amqp Use the MQ of the AMQP protocol, such as rabbit, to implement the distribution of events across JVMs
 - distributed-commandbus-jgroups Use Jgroup to implement Command distribution across JVM
 - distributed-commandbus-springcloud Integration with Spring Cloud, Command Discovery across JVM using DiscoveryClient and RESTemplate metrics Provide monitoring related information
 - mongo Axon and mongoDB integration
 - spring-boot-autoconfigure To implement Spring's autoconfigure support, you only need to provide related properties to automatically configure Axon
 - spring-boot-starter-jgroups Use distributed-commandbus-jgroups with spring autoconfigure to provide jgroup "one-click" integration
 - spring-boot-starter Integrate with springboot
 - spring Provide various annotations, integrated with spring


## Example 1
첫번째 예제애서는 은행 계좌에 예금을 입출금하는 예제를 이용하여 Axon Framework의 기본적인 작동 원리를 이해한다.


### Scenairio
 계죄를 개설하고, 입금, 출금을 한다.

### Aggregate
 Collection of Entity (Entity 집합체)  
 DDD에서 약간 내용 추가 필요


#### BankAccount 클래스

``` java
 public class BankAccount {

     @AggregateIdentifier
     private AccountId accountId;
     private String accountName;
     private BigDecimal balance;
 }

```
 @Aggregate을 이용해서 Aggregate Class를 선언할 수 있는데, 각각의 Aggregate은 식별자인 GUID를 가져야 하며, @AggregateIdentifier로 구분한다.   

AggregateIdentifier는
 - 비교를 위해서 equal 과 hashCode 메소드를 구현하며  
 - Serializable 인터페이스를 상속한다.


``` java

public class AccountId implements Serializable {

    private static final long serialVersionUID = 7119961474083133148L;
    private final String identifier;

    private final int hashCode;

    public AccountId() {
        this.identifier = IdentifierFactory.getInstance().generateIdentifier();
        this.hashCode = identifier.hashCode();
    }

    public AccountId(String identifier) {
        Assert.notNull(identifier, ()->"Identifier may not be null");
        this.identifier = identifier;
        this.hashCode = identifier.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountId accountId = (AccountId) o;

        return identifier.equals(accountId.identifier);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return identifier;
    }

}

```
#### Command

CQRS에서 모든 Write(CUD)는 Command Class에서 처리한다. Axon에서는 모든 Command는 POJO를 구성한다.
Axon은 Event-Driven Architecture를 기반으로 만들어져 있기 때문에, 내부적으로는 "CommandMessage"로 encapsulation된다.


```java
public class CreateAccountCommand {
    private AccountId accountId;
    private String accountName;
    private long amount;
    public CreateAccountCommand(AccountId accountId, String accountName, long amount) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.amount = amount;
    }
    //getter & setter
    ...
}


public class WithdrawMoneyCommand {
    @TargetAggregateIdentifier
    private AccountId accountId;
    private long amount;
    public WithdrawMoneyCommand(AccountId accountId, long amount) {
        this.accountId = accountId;
        this.amount = amount;
    }
    //getter & setter
    ...
}
```

#### Event
Event는 시스템의 변화를 발생할때 생성되는 Event Classs이며, 기본적으로 Aggregate에 변경이 가해질때 발생하며, Command와 마찬가지고 POJO이다.
Event는 EventMessage로 encapsulation된다.
```java
public class AccountCreatedEvent {
    private AccountId accountId;
    private String accountName;
    private long amount;
    public AccountCreatedEvent(AccountId accountId, String accountName, long amount) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.amount = amount;
    }
    //getter & setter
    ...
}

public class MoneyWithdrawnEvent {
    private AccountId accountId;
    private long amount;

    public MoneyWithdrawnEvent(AccountId accountId, long amount) {
        this.accountId = accountId;
        this.amount = amount;
    }
    //getter & setter
    ...
}

```

#### CommandHandler
Axon에서는 Command Handler를 지정하기 위해서 @CommandHandler를 사용한다.
아래와 같이 설정하면, Command가 발생할때 Command-CommandHanlder쌍(key-value)으로 작동한다.

```java
@CommandHandler
public BankAccount(CreateAccountCommand command){
   apply(new AccountCreatedEvent(command.getAccountId(), command.getAccountName(), command.getAmount()));
}

@CommandHandler
public void handle(WithdrawMoneyCommand command){
   apply(new MoneyWithdrawnEvent(command.getAccountId(), command.getAmount()));
}

```
위의 예제는 단순이 Event를 생성하고, static method인 apply를 호출해서 event를 발생시킨다.


#### EventHandler
@EventHanldere는 event 처리기 역할를 하는 메소드를 지정한다.

```java

@EventHandler
public void on(AccountCreatedEvent event){
    this.accountId = event.getAccountId();
    this.accountName = event.getAccountName();
    this.balance = new BigDecimal(event.getAmount());
    LOGGER.info("Account {} is created with balance {}", accountId, this.balance);
}

@EventHandler
public void on(MoneyWithdrawnEvent event){
    BigDecimal result = this.balance.subtract(new BigDecimal(event.getAmount()));
    if(result.compareTo(BigDecimal.ZERO)<0)
        LOGGER.error("Cannot withdraw more money than the balance!");
    else {
        this.balance = result;
        LOGGER.info("Withdraw {} from account {}, balance result: {}", event.getAmount(), accountId, balance);
    }
}

```


```java
public class Application {
    private static final Logger LOGGER = getLogger(Application.class);
    public static void main(String args[]){
        Configuration config = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(BankAccount.class)
                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                .buildConfiguration();
        config.start();
        AccountId id = new AccountId();
        config.commandGateway().send(new CreateAccountCommand(id, "MyAccount",1000));
        config.commandGateway().send(new WithdrawMoneyCommand(id, 500));
        config.commandGateway().send(new WithdrawMoneyCommand(id, 500));
    }
}
```


## Example 2
두번째 예제에서는 JPA를 이용해서 Aggregate의 상태 정보를 저장하는 예제를 구현한다.
JPA를 구현학 위해서 Transaction Manager를 설정하는 코드가 추가된다.

### 1. Update Maven dependency
 - Springboot 사용
 - spring-boot-starter-data-jpa
 - my-sql-connector
 - spring-boot-starter-web

 ```

 <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>1.5.2.RELEASE</version>
</parent>

<dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
        </dependency>
    </dependencies>

```
### 2. Database 접속 정보 추가 application.properties

```
# Datasource configuration
spring.datasource.url=jdbc:mysql://xxx.xxx.xxx.xxx:3306/cqrs
spring.datasource.driverClassName=com.mysql.jdbc.Driver
spring.datasource.username=<username>
spring.datasource.password=<password>
spring.datasource.validation-query=SELECT 1;
spring.datasource.initial-size=2
spring.datasource.sql-script-encoding=UTF-8

spring.jpa.database=mysql
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=create-drop

```
### 3. Spring Configuration 추가

Axon에서 JPA를 사용하기 위한 설정을 추가한다.

```java
@Configuration
@EnableAxon
public class JpaConfig {

    private static final Logger LOGGER = getLogger(JpaConfig.class);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Bean
    public EventStorageEngine eventStorageEngine(){
        return new InMemoryEventStorageEngine();
    }

    @Bean
    public TransactionManager axonTransactionManager() {
        return new SpringTransactionManager(transactionManager);
    }

    @Bean
    public EventBus eventBus(){
        return new SimpleEventBus();
    }

    @Bean
    public CommandBus commandBus() {
        SimpleCommandBus commandBus = new SimpleCommandBus(axonTransactionManager(), NoOpMessageMonitor.INSTANCE);
        //commandBus.registerHandlerInterceptor(transactionManagingInterceptor());
        return commandBus;
    }

    @Bean
    public TransactionManagingInterceptor transactionManagingInterceptor(){
        return new TransactionManagingInterceptor(new SpringTransactionManager(transactionManager));
    }

    @Bean
  	public EntityManagerProvider entityManagerProvider() {
  		return new ContainerManagedEntityManagerProvider();
  	}

	  @Bean
    public Repository<BankAccount> accountRepository(){
        return new GenericJpaRepository<BankAccount>(entityManagerProvider(),BankAccount.class, eventBus());
    }
}
```

현재 시점(Axon version 3.3 이상)에서는 @EnableAxon 이 depricated 되었고 "axon-spring-boot-autoconfigure"로 대체되었다.

이렇게하면 자동으로 설정 모든이 Inject된다.
위에서 Event Store를 InMemory에 (InMemoryEventStorageEngine) 저장하도록 설정하고, Aggregate의 상태정보는 MySQL에 저장한다.
Axon는 Aggregate마다 AggregateReposiotyBean을 생성하다. 위 예제에서는 GenericJpaRepository로 BankAccout Aggregate의 상태정보를 저장한다.


### 4. Aggregate에 JPA Entity annotations 추가

위에서 정의한(JpaConfig) Repository를 Aggregate에 할당한다.

JPA requires that the Entity must have an ID. GenericJpaRepositoryBy default, String is used as the type of the EntityId. This does not use the String directly. The
java.lang.IllegalArgumentException: Provided id of the wrong type for class com.edi.learn.axon .aggregates.BankAccount. Expected: class com.edi.learn.axon.domain.AccountId, got class java.lang.String The
solution is to add @Id, @Column to the getter method.



```java
@Aggregate(repository = "accountRepository")
@Entity
public class BankAccount {
  @AggregateIdentifier
  private AccountId accountId;

  ......

  @Id
  public String getAccountId() {
      return accountId.toString();
  }

  @Column
  public String getAccountName() {
      return accountName;
  }

  @Column
  public BigDecimal getBalance() {
      return balance;
  }
}

```


### 5. Rest Controller
***변경 필요 ***
***입력 출력 REST EndPoint 추가 ****


``` java
@RestController
@RequestMapping("/bank")
public class BankAccountController {

    private  static  final logger LOGGER = getLogger (BankAccountController.class);

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private HttpServletResponse response;

    @RequestMapping(method = RequestMethod.POST)
    public void create() {
        LOGGER.info("start");
        AccountId id = new AccountId();
        LOGGER.debug("Account id: {}", id.toString());
        commandGateway.send(new CreateAccountCommand(id, "MyAccount",1000));
        commandGateway.send(new WithdrawMoneyCommand(id, 500));
        commandGateway.send(new WithdrawMoneyCommand(id, 300));
        commandGateway.send(new CreateAccountCommand(id, "MyAccount", 1000));
        commandGateway.send(new WithdrawMoneyCommand(id, 500));
    }
}
```

### 6. StartUp class

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.edi.learn"})
public class Application {  
    public static void main(String args[]){
        SpringApplication.run(Application.class, args);
    }
}
```
### 7. Test
PostMan등을 이용해서  http://localhost:8008/bank POST Request!



## Example 3
세변째 예제에서는 두번제 예제에 이어 Axon에서 제공하는 spring-boot-autoconfigure feature를 통한  자동 설정과 Event Store에 Domain Event를 저장하는 예시이다.


 - spring-boot-autoconfigure
 - JpaConfig Class 삭제
 - BankAccout Entity 선언 삭제

AxonAutoConfiguration 내부에서 CommandBus, EventBus, EventStorageEngine, Serializer, EventStore등을 선언한다.

```java
@ConditionalOnBean (EntityManagerFactory.class)
@RegisterDefaultEntities (packages = { "org.axonframework.eventsourcing.eventstore.jpa" , "org.axonframework.eventhandling.tokenstore" , "org.axonframework.eventhandling.saga.repository.jpa" }) @Configuration public static class JpaConfiguration {





    @ConditionalOnMissingBean @Bean public EventStorageEngine eventStorageEngine (EntityManagerProvider entityManagerProvider,                                                  TransactionManager transactionManager) { return new JpaEventStorageEngine(entityManagerProvider, transactionManager);     }






    @ConditionalOnMissingBean @Bean public EntityManagerProvider entityManagerProvider () { return new ContainerManagedEntityManagerProvider();     }





    @ConditionalOnMissingBean @Bean public TokenStore tokenStore (Serializer serializer, EntityManagerProvider entityManagerProvider) { return new JpaTokenStore(entityManagerProvider, serializer);     }





    @ConditionalOnMissingBean (SagaStore.class) @Bean public JpaSagaStore sagaStore (Serializer serializer, EntityManagerProvider entityManagerProvider) { return new JpaSagaStore(serializer, entityManagerProvider);     } }





```
위와 같이 자동 설정과 mysql-connector를 설정하고 실행하면, MySQL에 아래와 같은 테이블이 자동 생성되는 것을 볼수 있다.
여기서 domain_event_entry에 모든 Aggregate의 상태변경을 야기하는 Event가 저장된다.

 - pay_load
 - pay_load_type은 java class에 따라 다르다????
 - time stamp는 이벤트 발생 시간을 나타낸다.
 - aggregate_identifier를 이용해서 aggregate를 추적한다.
 - sequence_number 는 같은 aggregate의 발생 순서



## Example 4
앞의 세걔의 예제를 통해서 기본적인 Axon Framework의 구현 메커니즘을 이해했다면, 네번째 예제에서는 Command용 저장소와 Query용 저장소를 불리한 CQRS+ES 예제를 다른다.

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)

CQRS에서는 Command-Side Repository와 Query-Side Repository를 별도로 가지도록 한다. 이 예제에서는 Command-Side는 MongoDB를, Query는 MySQL을 이용하도록 한다.

#### 시나리오
백오피스의 직원이 쇼핑몰에 신규 상품을 생성하면, 고객이 상품 아이템을 선택해서 주문을 하고 결제를 하는 시나리오이다.

Product (id, name, stock, price)

상품 추가 프로세스
CreateProductCommand -> new ProductAggregate instance -> ProductCreatedEvent

여기서 주로 Event는 과거에 일어난 이벤트로 과거시제를 주로 사용한다.

Order (id, username, payment, products)
주문 프로세스
CreateOrderCommand-> new OrderAggregateinstance -> OrderCreatedEvent

### Command-Side

#### Aggregate
```java
@Aggregate
public class ProductAggregate {

    private static final Logger LOGGER = getLogger(ProductAggregate.class);

    @AggregateIdentifier
    private String id;
    private String name;
    private int stock;
    private long price;

    public ProductAggregate() {
    }

    @CommandHandler
    public ProductAggregate(CreateProductCommand command) {
        apply(new ProductCreatedEvent(command.getId(),command.getName(),command.getPrice(),command.getStock()));
    }

    @EventHandler
    public void on(ProductCreatedEvent event){
        this.id = event.getId();
        this.name = event.getName();
        this.price = event.getPrice();
        this.stock = event.getStock();
        LOGGER.debug("Product [{}] {} {}x{} is created.", id,name,price,stock);
    }

    // getter and setter
    ......
}


@Aggregate
public class OrderAggregate {

    @AggregateIdentifier
    private OrderId id;
    private String username;
    private double payment;

    @AggregateMember
    private Map<String, OrderProduct> products;

    public OrderAggregate(){}

    public OrderAggregate(OrderId id, String username, Map<String, OrderProduct> products) {
        apply(new OrderCreatedEvent(id, username, products));
    }

    public OrderId getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Map<String, OrderProduct> getProducts() {
        return products;
    }

    @EventHandler
    public void on(OrderCreatedEvent event){
        this.id = event.getOrderId();
        this.username = event.getUsername();
        this.products = event.getProducts();
        computePrice();
    }

    private void computePrice() {
        products.forEach((id, product) -> {
            payment += product.getPrice() * product.getAmount();
        });
    }

    /**
     * Divided 100 here because of the transformation of accuracy
     *
     * @return
     */
    public double getPayment() {
        return payment/100;
    }

    public void addProduct(OrderProduct product){
        this.products.put(product.getId(), product);
        payment += product.getPrice() * product.getAmount();
    }

    public void removeProduct(String productId){
        OrderProduct product = this.products.remove(productId);
        payment = payment - product.getPrice() * product.getAmount();
    }
}
```


```java

@Component
public class OrderHandler {

    private  static  final logger LOGGER = getLogger (OrderHandler.class);

    @Autowired
    private Repository<OrderAggregate> repository;

    @Autowired
    private Repository<ProductAggregate> productRepository;

    @Autowired
    private EventBus eventBus;

    @CommandHandler
    public void handle(CreateOrderCommand command) throws Exception {
        Map<String, OrderProduct> products = new HashMap<>();
        command.getProducts().forEach((productId,number)->{
            LOGGER.debug("Loading product information with productId: {}",productId);
            Aggregate<ProductAggregate> aggregate = productRepository.load(productId);
            products.put(productId,
                    new OrderProduct(productId,
                            aggregate.invoke(productAggregate -> productAggregate.getName()),
                            aggregate.invoke(productAggregate -> productAggregate.getPrice()),
                            number));
        });
        repository.newInstance(() -> new OrderAggregate(command.getOrderId(), command.getUsername(), products));
    }
}

```
