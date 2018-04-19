# CQRS-ESwithAxon

본 Hans-On Lab에서는 Axon Framework를 활용해서 CQRS와 Event Sourcing에 대해서 실습한다.




## Axon Framework
Axon Framework은 Event-Driven lightweight CQRS framework으로 Aggregate의 상태정보를 저장하는 방법과 EventSourcing을 지원한다.


### CQRS Architecture

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)


### Axon Framework Building Block

![Axon Building Block](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs.png)

### Command/Write Side

#### Command

![Command, CommandBus, CommandHandlers](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs-command-handling-component.png)

Command는 시스템에 변경을 일으키는(CUD) 액션을 알리는 객체로, CommandHandler에 데이터를 전달하는 역할을 한다.
Command는 POJO기반으로 생성하기 때문에 어떤 Interface나 Class를 상속할 필요가 없으며, Class 의도를 명확하게 나타내가 위해서 다음과 같은 명명규칙을 권고한다.
 ex) CancelAppointmentCommand, AddItemCommand 등

#### Command Bus
Command Bus는 Command를 받아서 해당 Command Handler에 라우팅을 하는 역할을 하는 컴포넌트이다.

 - SimpleCommandBus
 - AsynchronousCommandBus : 비동기
 - DisruptorCommandBus : 분산환경

#### Command Handler
Command Handler는 command를 받아서 Action를 처리하는 처리기 역할을 한다.

Command Handler 생성 방법
 - CommandHandler 인터페이스 상속
 - Spring F/W를 사용하는 경우 @CommandHanlder 사용 (AnnotationCommandHandlerBeanPostProcessor)

```java
 @Bean
  public SimpleCommandBus commandBus() {
    SimpleCommandBus simpleCommandBus =
                     new SimpleCommandBus();

    // This manually subscribes the command handler:
    // DebitAccountHandler to the commandbus
    simpleCommandBus.subscribe(DebitAccount.class.getName(), 
                     new DebitAccountHandler());
        return simpleCommandBus;
  }
```

#### Command Gateway
Command bus를 직접구현할 수도 있지만, 주로 Command Gateway를 사용하는게 편한데, command 실패시 Retry등의 메커니즘이 적용 되기 때문이다.


#### Repository

![Repository](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqlp-repository-side.png)

Aggregate의 상태변화를 저장한다.



### Query/Read Side

![Event, Event Bus, EventHandlers](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs-events.png)




![Command, CommandBus, CommandHandlers](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs-command-handling-component.png)

![Event, Event Bus, EventHandlers](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs-events.png)



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
앞의 세개의 예제를 통해서 기본적인 Axon Framework의 구현 메커니즘을 이해했다면, 네번째 예제에서는 Command용 저장소와 Query용 저장소를 불리한 CQRS+ES 예제를 다른다.

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)

CQRS에서는 Command-Side Repository와 Query-Side Repository를 별도로 가지도록 한다. 이 예제에서는 Command-Side는 MongoDB를, Query는 MySQL을 이용하도록 한다.

#### 시나리오
>백오피스의 직원이 쇼핑몰에 신규 상품을 생성하면, 고객이 상품 아이템을 선택해서 주문을 하고 결제를 하는 시나리오이다.
>Product (id, name, stock, price)
>상품 추가 프로세스
>CreateProductCommand -> new ProductAggregate instance -> ProductCreatedEvent
>여기서 주로 Event는 과거에 일어난 이벤트로 과거시제를 주로 사용한다.
>Order (id, username, payment, products)
>주문 프로세스  
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
여기서 CreateOrderCommand를 OrderAggregate에서 뺐는데(ProductAggregate와 다르게), order할때 product의 unit price를 알아야 하기 때문에, Product id를 가지고 query를 해서 order를 생성해야 하기 때문에, OrderHandler를 별도로 뺐다.

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
org.axonframework.commandhandling.model.Repository<T> 에는 아래와 같이 3개의 메소드가 있는데, Delete와 Update가 없다.
모든 Aggregate에 발생하는 변화를 저장하기 때문 Update와 Delete 모두 Event를 Append하고 Delete는 flag를 Invalid 표시한다.

```java
public interface Repository<T> {

    /**
     * Load the aggregate with the given unique identifier. No version checks are done when loading an aggregate,
     * meaning that concurrent access will not be checked for.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @return The aggregate root with the given identifier.
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     */
    Aggregate<T> load(String aggregateIdentifier);

    /**
     * Load the aggregate with the given unique identifier.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @param expectedVersion     The expected version of the loaded aggregate
     * @return The aggregate root with the given identifier.
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     */
    Aggregate<T> load(String aggregateIdentifier, Long expectedVersion);

    /**
     * Creates a new managed instance for the aggregate, using the given {@code factoryMethod}
     * to instantiate the aggregate's root.
     *
     * @param factoryMethod The method to create the aggregate's root instance
     * @return an Aggregate instance describing the aggregate's state
     * @throws Exception when the factoryMethod throws an exception
     */
    Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception;
}
```



### Command

Web-based Rest Controller(spring-boot-starter-web)
```java
@RestController
@RequestMapping("/product")
public class ProductController {

    private static final Logger LOGGER = getLogger(ProductController.class);

    @Autowired
    private CommandGateway commandGateway;

    @RequestMapping(value = "/{id}", method = RequestMethod.POST)
    public void create(@PathVariable(value = "id") String id,
                       @RequestParam(value = "name", required = true) String name,
                       @RequestParam(value = "price", required = true) long price,
                       @RequestParam(value = "stock",required = true) int stock,
                       HttpServletResponse response) {

        LOGGER.debug("Adding Product [{}] '{}' {}x{}", id, name, price, stock);

        try {
            // multiply 100 on the price to avoid float number
            CreateProductCommand command = new CreateProductCommand(id,name,price*100,stock);
            commandGateway.sendAndWait(command);
            response.setStatus(HttpServletResponse.SC_CREATED);// Set up the 201 CREATED response
            return;
        } catch (CommandExecutionException cex) {
            LOGGER.warn("Add Command FAILED with Message: {}", cex.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (null != cex.getCause()) {
                LOGGER.warn("Caused by: {} {}", cex.getCause().getClass().getName(), cex.getCause().getMessage());
                if (cex.getCause() instanceof ConcurrencyException) {
                    LOGGER.warn("A duplicate product with the same ID [{}] already exists.", id);
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                }
            }
        }
    }
}

```

commandGateway 에 4가지 메소드
 - Send(command, CommandCallback) Send command, call CommandCallbackin the method onSuccessor onFailuremethod
 - sendAndWait(command) sends the command, waits for the execution to complete and returns the result
 - sendAndWait(command, timeout, TimeUnit) This is well understood and there is a timeout more than the above
 - Send(command) This method returns one CompletableFuture, without waiting for the command to execute, return immediately. The result is obtained by future.


### Repository

디폴트 jpa 대신에 "axon-mongo"를 사용하기 때문에 Aggregate Repository를 추가해 줘야 한다.

```java
@Configuration
public class ProductConfig {

    @Autowired
    private EventStore eventStore;

    @Bean
    @Scope("prototype")
    public ProductAggregate productAggregate(){
        return new ProductAggregate();
    }

    @Bean
    public AggregateFactory<ProductAggregate> productAggregateAggregateFactory(){
        SpringPrototypeAggregateFactory<ProductAggregate> aggregateFactory = new SpringPrototypeAggregateFactory<>();
        aggregateFactory.setPrototypeBeanName("productAggregate");
        return aggregateFactory;
    }

    @Bean
    public Repository<ProductAggregate> productAggregateRepository(){
        EventSourcingRepository<ProductAggregate> repository = new EventSourcingRepository<ProductAggregate>(
                productAggregateAggregateFactory(),
                eventStore
        );
        return repository;
    }
}

```

With the EventSourcingRepository, an AggregateFactory must be specified to reflect Aggregates, so we define the Aggregate prototype here and register it with the AggregateFactory.
In this way, when the system starts, reading history events for ES restore, you can truly reproduce the state of Aggregate.

### Configuration

```
# mongo
mongodb.url=10.1.110.24
mongodb.port=27017
# mongodb.username=
# mongodb.password=
mongodb.dbname=axon
mongodb.events.collection.name=events
mongodb.events.snapshot.collection.name=snapshots

```

```java
@Configuration
public class CommandRepositoryConfiguration {

    @Value("${mongodb.url}")
    private String mongoUrl;

    @Value("${mongodb.dbname}")
    private String mongoDbName;

    @Value("${mongodb.events.collection.name}")
    private String eventsCollectionName;

    @Value("${mongodb.events.snapshot.collection.name}")
    private String snapshotCollectionName;

    @Bean
    public Serializer axonJsonSerializer() {
        return new JacksonSerializer();
    }

    @Bean
    public EventStorageEngine eventStorageEngine(){
        return new MongoEventStorageEngine(
                axonJsonSerializer(),null, axonMongoTemplate(), new DocumentPerEventStorageStrategy());
    }

    @Bean(name = "axonMongoTemplate")
    public MongoTemplate axonMongoTemplate() {
        MongoTemplate template = new DefaultMongoTemplate(mongoClient(), mongoDbName, eventsCollectionName, snapshotCollectionName);
        return template;
    }

    @Bean
    public MongoClient mongoClient(){
        MongoFactory mongoFactory = new MongoFactory();
        mongoFactory.setMongoAddresses(Arrays.asList(new ServerAddress(mongoUrl)));
        return mongoFactory.createMongo();
    }
}

```


### Query Side
Query Side는 특이할 점이 없다.

```java
@Entity
public class ProductEntry {

  @Id
  private String id;
  @Column
  private String name;
  @Column
  private long price;
  @Column
  private int stock;

  public ProductEntry() {
  }

  public ProductEntry(String id, String name, long price, int stock) {
      this.id = id;
      this.name = name;
      this.price = price;
      this.stock = stock;
  }
  // getter & setter
  ......
}

@Entity
public class OrderEntry {
  @Id
  private String id;
  @Column
  private String username;
  @Column
  private double payment;
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  @JoinColumn(name = "order_id")
  @MapKey(name = "id")
  private Map<String, OrderProductEntry> products;

  public OrderEntry() {
  }

  public OrderEntry(String id, String username, Map<String, OrderProductEntry> products) {
      this.id = id;
      this.username = username;
      this.payment = payment;
      this.products = products;
  }
  // getter & setter
  ......
}

@Entity
public class OrderProductEntry {
  @Id
  @GeneratedValue
  private Long jpaId;
  private String id;
  @Column
  private String name;
  @Column
  private long price;
  @Column
  private int amount;

  public OrderProductEntry() {
  }

  public OrderProductEntry(String id, String name, long price, int amount) {
      this.id = id;
      this.name = name;
      this.price = price;
      this.amount = amount;
  }

  // getter & setter
  ......
}
```


### 실습

```
//MySql image 다운로드
docker pull mysql
//mongodb image 다운로드
docker pull mongo

//MySql 컨테이너 기동
docker run -p 3306:3306 --name mysql1 -e MYSQL_ROOT_PASSWORD=Welcome1 -d mysql
//mongodb 컨테이너 기동
docker run -p 27017:27017 --name mongodb -d mongo

// MySql 데이터 베이스 생성 CQRS
docker exec -it mysql1 bash
$mysql -uroot -p
Enter Password :
mysql> create database cqrs; -- Create the new database
mysql> grant all on cqrs.* to 'root'@'localhost';

select host, user from mysql.user;

// 별도의 shell에서 mongodb 컨테이너 접속
docker exec -it mongodb bash
```

 1. Product 생성
 POST http://127.0.0.1:8080/product/1?name=SoccerBall&price=10&stock=100

 2. Order 생성
 POST http://127.0.0.1:8080/order

JSON
{
	"username":"Daniel",
	"products":[{
		"id":1,
		"number":90
	}]
}

```
> use axon
> show collections
events
snapshots
system.indexes
> db.events.find().pretty()
{
  "_id" : ObjectId("58dd181073bc0c0fb86d895e"),
  "aggregateIdentifier" : "1",
  "type" : "ProductAggregate",
  "sequenceNumber" : NumberLong(0),
  "serializedPayload" : "{\"id\":\"1\",\"name\":\"ttt\",\"price\":1000,\"stock\":100}",
  "timestamp" : "2017-03-30T14:37:04.075Z",
  "payloadType" : "com.edi.learn.axon.common.events.ProductCreatedEvent",
  "payloadRevision" : null,
  "serializedMetaData" : "{\"traceId\":\"4a298ed4-0d53-402a-ae6b-d79cc5e193bf\",\"correlationId\":\"4a298ed4-0d53-402a-ae6b-d79cc5e193bf\"}",
  "eventIdentifier" : "500f3a8f-7c02-4e8e-bb9c-7b676224ce5c"
}

```

MySql Query
select * from cqrs.product_entry;


GET http://localhost:8080/products
GET http://localhost:8080/products/1



*** 조금 더 해야 한다. ***
