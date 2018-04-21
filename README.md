# CQRS-ESwithAxon

본 Hans-On Lab에서는 Axon Framework를 활용해서 CQRS와 Event Sourcing에 대해서 실습합니다.


# Pre-Requisite

 - git 설치 : https://git-scm.com/book/ko/v1/시작하기-Git-설치
 - docker : https://docs.docker.com/install/
 - JDK1.8 : http://www.oracle.com/technetwork/java/javase/downloads/index.html
 - maven : http://maven.apache.org/download.cgi
 - Java IDE(Eclipse, IntelliJ등 )

``` bash
//Source Code 다운로드  
git clone https://github.com/DannyKang/CQRS-ESwithAxon

//MySql image 다운로드  
docker pull mysql

//mongodb image 다운로드  
docker pull mongo

//docker container 초기화 (혹시나 동일한 이름이 있을 수 있기 때문에)
docker rm mysql1
docker rm mongodb
```


# Axon Framework
Axon Framework은 Event-Driven lightweight CQRS framework으로 Aggregate의 상태정보를 저장하는 방법과 EventSourcing을 지원합니다.


## Axon Framework Architecture
![Axon Framework Architecture](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs.png)


## Axon Framework Building Block

 - Command, Command Handler
 - Command Bus, Command Gateway
 - Repository
 - Event, Event Handler
 - Event Bus

## Command/Write Side

### 1. Command

![Command, CommandBus, CommandHandlers](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs-command-handling-component.png)

Command는 시스템에 변경을 일으키는(CUD) 액션을 알리는 객체로, CommandHandler에 데이터를 전달하는 역할을 합니다.
Command는 POJO기반으로 생성하기 때문에 어떤 Interface나 Class를 상속할 필요가 없으며, Command의 의도를 명확하게 나타내기 위해서 다음과 같은 명명규칙을 권고합니다.  
 ex) CancelAppointmentCommand, AddItemCommand 등

### 2. Command Bus
Command Bus는 Command를 받아서 해당 Command Handler에 라우팅 역할을 하는 컴포넌트(Message Dispatching)입니다.

 - SimpleCommandBus
 - AsynchronousCommandBus : 비동기
 - DisruptorCommandBus : 분산환경

 ``` java
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


### 3. Command Handler
Command Handler는 command를 받아서 Action를 처리하는 처리기 역할을 합니다.  
하나의 Command는 하나의 Command Handler만 가질 수 있습니다.  
(반면 Event는 여러개의 Event Handlder를 가질 수 있습니다.)

****Command Handler 생성 방법****
 - CommandHandler 인터페이스 상속
 - Spring F/W를 사용하는 경우 *@CommandHanlder* 사용 (AnnotationCommandHandlerBeanPostProcessor)



### 4. Command Gateway
Command bus를 직접구현할 수도 있지만, 주로 Command Gateway를 사용하는게 편한데, command 실패시 Retry등의 메커니즘이 적용 되기 때문입니다.


### 5. Repository 

![Repository](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqlp-repository-side.png)

Repository는 Aggregate의 상태정보나, Event를 저장합니다.

 - GenericJpaRepository
 JPA를 구현하기 위해서는 EntityManager를 직접 구현해야 한다.


## Query/Read Side

![Event, Event Bus, EventHandlers](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/cqrs-events.png)



### 1. Event
Command(Write) 에서, Domain Model/Aggregate에 상태를 변경했다면, 이 상태변화(Domain Event)를 전파하는 역할을 하는것이 Event입니다.  
Command Handler는 변경사항(Domain Event)를 만들어서 Event Bus를 통해서 Event Handler에 Event를 전달합니다.  
Command가 앞으로 일어날일을 기술하는데 반해 Event는 과거에 일어난 사건(이미 일어난 사건)을 기술하는 것으로 주로 과거형을 사용합니다.

 ex) AccountAddedEvent, ItemDeletedEvent 등

### 2. Event Handler
전달된 Event를 이용하여 Query 전용 (Material View) Storage에 데이터를 생성합니다.  
Command-Commandler 쌍이 1:1로 각각 하나씩만 있어야 하는데 반해서, Event는 하나의 Event에 대해서, 여러개의 Event Handler가 있을 수 있습니다.


#### Axon framework 이용하기

Axon framework을 이용하기 위해서는 Maven dependency를 추가하면 됩니다.
``` java
<dependency>
  <groupId>org.axonframework</groupId>
  <artifactId>axon-core</artifactId>
  <version>${axon.version}</version>
</dependency>
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring</artifactId>
    <version>${axon.version}</version>
</dependency>
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring-boot-autoconfigure</artifactId>
    <version>${axon.version}</version>
</dependency>
.....
```


## Example 1
첫번째 예제에서는 은행 계좌에 예금을 입/출금하는 예제를 이용하여 Axon Framework의 기본적인 작동 원리를 이해합니다.


### Scenairio
 은행 계좌를 개설하고, 입/출금(Command)을 생성합니다.

#### BankAccount 클래스

``` java
 public class BankAccount {

     @AggregateIdentifier
     private AccountId accountId;
     private String accountName;
     private BigDecimal balance;
 }

```
#### Aggregate
 Collection of Objects (Entity 집합체)  

 @Aggregate을 이용해서 Aggregate Class를 선언할 수 있으며, 각각의 Aggregate을 구분하기 위한 식별자(GUID)를 가지며, @AggregateIdentifier 로 선언합니다.

AggregateIdentifier는 비교를 위해서
 - equal 과 hashCode 메소드를 구현하며  
 - Serializable 인터페이스를 상속합니다.


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

CQRS에서 모든 Write(CUD)는 Command Class에서 처리하며, Axon에서는 모든 Command는 POJO를 구성합니다.   
Axon은 내부적으로 Event-Driven Architecture(Message)를 기반으로 만들어져 있기 때문에, 내부적으로는 "CommandMessage"로 encapsulation된다.


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
Event는 시스템의 변화를 발생할때 생성되는 Event Class이며, 기본적으로 Aggregate에 변경이 가해질때 발생하며, Command와 마찬가지고 POJO입니다.
Event는 EventMessage로 encapsulation됩니다.

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
Axon에서는 Command Handler를 지정하기 위해서 @CommandHandler를 사용합니다.  
아래와 같이 설정하면, Command가 발생할때 Command-CommandHandler쌍(key-value)으로 작동합니다.

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
위의 예제는 단순히 Event를 생성하고, static method인 apply를 호출해서 event를 발생시킨다.  
***Axon은 apply Method를 발생시키면 내부적으로 Event Store에 해당 이벤트를 저장하고, EventBus를 통해서 Event를 Publish합니다.***


#### EventHandler
@EventHandler는 event 처리기 역할를 하는 메소드를 지정합니다.

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

#### Application Main

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


***Quiz***


## Example 2
두번째 예제에서는 JPA를 이용해서 Aggregate의 상태 정보를 저장하는 예제를 구현합니다.
JPA를 구현하기 위해서 Transaction Manager를 설정하는 코드가 추가되어야 합니다.

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

현재 시점(Axon version 3.3 이상)에서는 @EnableAxon 이 deprecated 되었고 "axon-spring-boot-autoconfigure"로 대체되었습니다.  
이렇게 설정하면 자동으로 Bean이 주입(inject)됩니다.   

위에서는 EventStoreEnginge를 InMemory에 (InMemoryEventStorageEngine) 저장하도록 설정하고, Aggregate의 상태정보는 MySQL에 저장합니다.
Axon는 Aggregate마다 AggregateReposiotyBean을 생성하다.

위 예제에서는 GenericJpaRepository로 BankAccout Aggregate의 상태정보를 저장합니다.


### 4. Aggregate에 JPA Entity annotations 추가

위에서 정의한(JpaConfig) Repository를 Aggregate에 할당합니다.  
JPA를 위해서 @Entity, @Id, @Column 추가합니다.




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
입/출금을 위한 REST Endpoint 추가

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
### Hands-On
 1. mysql 실행

 ****MySQL용 Docker Image****
 ```
 //MySql image 다운로드
 docker pull mysql

 //MySql 컨테이너 기동
 docker run -p 3306:3306 --name mysql1 -e MYSQL_ROOT_PASSWORD=Welcome1 -d mysql

 // MySql 데이터 베이스 생성 CQRS
 docker exec -it mysql1 bash
 $mysql -uroot -p
 Enter Password : Welcome1
 mysql> create database cqrs; -- Create the new database
 mysql> grant all on cqrs.* to 'root'@'localhost';

 // Query
 mysql>use cqrs;
 mysql> select * from bank_account;

 ```

 2. App 실행
 3. POST http://localhost:8080/bank
   PostMan등을 이용해서  http://localhost:8080/bank POST Request!
```
curl -X POST http://localhost:8080/bank
```




## Example 3
세번째 예제는 두번제 예제에 Aggregate의 Domain Event를 저장하는 EventSourcing을 적용하는 예제입니다.
Axon에서 제공하는 axon-spring-boot-autoconfigure 기능을 이용해서 자동으로 Domain Event가 MySql로 설정된 Event Store에 저장됩니다.

 - axon-spring-boot-autoconfigure 추가
 - JpaConfig Class 삭제
 - BankAccout Entity 선언 삭제

```
//pom.xml
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
</dependency>
```

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


![Event Store](https://github.com/DannyKang/CQRS-ESwithAxon/blob/master/images/domain_event_entry)

 - pay_load
 - pay_load_type
 - time stamp
 - aggregate_identifier
 - sequence_number


 ### Hands-on
  1. mysql 실행
  2. App 실행
  3. POST http://localhost:8080/bank

```
 curl -X POST http://localhost:8080/bank
```

```
 // Query
 mysql>use cqrs;
 mysql>select * from domain_event_entry;

```




## Example 4
앞의 세개의 예제를 통해서 기본적인 Axon Framework의 구현 메커니즘을 이해했다면, 네번째 예제에서는 Command용 저장소와 Query용 저장소를 불리한 CQRS+ES 예제를 다릅니다.

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)

CQRS에서는 Command-Side Repository와 Query-Side Repository를 별도로 가지도록 합니다.    
이 예제에서는 Command-Side는 MongoDB를, Query는 MySQL을 이용합니다.

#### 시나리오
>백오피스의 직원이 쇼핑몰에 신규 상품을 생성하면, 고객이 상품 아이템을 선택해서 주문을 하고 결제를 하는 시나리오입니다.
> *Product (id, name, stock, price)*,  *Order (id, username, payment, products)*    
>상품 추가 프로세스  
>CreateProductCommand -> new ProductAggregate instance -> ProductCreatedEvent  
>
>주문 프로세스    
>CreateOrderCommand-> new OrderAggregateinstance -> OrderCreatedEvent

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
 - Send(command, CommandCallback) : Send command, call CommandCallbackin the method on Successor on Failure Method
 - sendAndWait(command) : sends the command, waits for the execution to complete and returns the result
 - sendAndWait(command, timeout, TimeUnit) :  This is well understood and there is a timeout more than the above
 - Send(command) :This method returns one CompletableFuture, without waiting for the command to execute, return immediately. The result is obtained by future.


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
EventSourcingRepository을 이용해서 각각의 Aggregate(ProductAggregate, OrderAggregate)에 대한 AggregateFactory를 설정해 준다.
이렇게 하면 Event Store에서 Event History를 읽어와서 Aggregate의 현재 상태를 생산(reproduce)한다.  

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
Query Side는 Command Side 에서 발생시킨 Event를 수신해서 조회용(Aggregate의 최종 상태값)을 JPA를 이용해서 저장한다.   

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

```java
// Query Event Handler

@Component
public class OrderEventHandler {

    private static final Logger LOGGER = getLogger(OrderEventHandler.class);

    @Autowired
    private OrderEntryRepository repository;

    @EventHandler
    public void on(OrderCreatedEvent event){
        Map<String, OrderProductEntry> map = new HashMap<>();
        event.getProducts().forEach((id, product)->{
            map.put(id,
                    new OrderProductEntry(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            product.getAmount()));
        });
        OrderEntry order = new OrderEntry(event.getOrderId().toString(), event.getUsername(), map);
        repository.save(order);
    }

}
```


### Hands-On
***MySQL Database 추가 생성 "ProductOrder"***
```
//MySql image 다운로드
docker pull mysql
//mongodb image 다운로드
docker pull mongo

//MySql 컨테이너 기동
docker run -p 3306:3306 --name mysql1 -e MYSQL_ROOT_PASSWORD=Welcome1 -d mysql
//mongodb 컨테이너 기동
docker run -p 27017:27017 --name mongodb -d mongo

// MySql 데이터 베이스 생성 ProductOrder
docker exec -it mysql1 bash
$mysql -uroot -p
Enter Password : Welcome1

//Example 4를 위한 별도의 DB 생성
mysql> create database productorder; -- Create the new database
mysql> grant all on productorder.* to 'root'@'localhost';

select host, user from mysql.user;

// 별도의 shell에서 mongodb 컨테이너 접속
docker exec -it mongodb bash
```

 1. Product 생성
 POST http://localhost:8080/product/1?name=SoccerBall&price=10&stock=100

 ```
 curl -d "name=SoccerBall&price=10&stock=200"  http://localhost:8080/product/1
 ```


 2. Order 생성
 POST http://localhost:8080/order

 ```
 curl -X POST http://localhost:8080/order -d @test.json -H "Content-Type: application/json"
 ```

JSON
{
	"username":"Daniel",
	"products":[{
		"id":1,
		"number":90
	}]
}

 3. Query DB  

```
$docker exec -it mongodb1
$mongo

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
 4. MySql Query
```
mysql>select * from cqrs.product_entry;
```


GET http://localhost:8080/products
GET http://localhost:8080/products/1

```
curl http://localhost:8080/products
curl http://localhost:8080/products/1  
```


# 참조자료

 - [Axon Framework CQRS EventSourcing](http://edisonxu.com)
 - [Exploring CQRS Architecture with Axon Framework](http://www.geekabyte.io/2015/10/exploring-cqrs-architecture-with-axon.html)
