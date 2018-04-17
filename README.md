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

```
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
세변째 예제에서는 JPA에 Aggregate의 상태 정보와 Event Store에 Domain Event를 저장하는 예시이다.

## Example 4
네번째 예제에서는 Command용 저장소와 Query용 저장소를 불리한 CQRS 예제를 다른다.
