# CQRS-ESwithAxon

Axon Framework은 Event-Driven lightweight CQRS framework으로 Aggregate의 상태정보를 저장하는 방법과 EventSourcing을 지원한다.

## Axon Framework Architecture

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)


### Axon Framework Building Block

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


### Example 1
첫번째 예제애서는 은행 계좌에 예금을 입출금하는 예제를 이용하여 Axon Framework의 기본적인 작동 원리를 이해한다.


### Example 2
두번째 예제에서는 JPA를 이용해서 Aggregate의 상태 정보를 저장하는 예제를 구현한다.
JPA를 구현학 위해서 Transaction Manager를 설정하는 코드가 추가된다.


### Example 3
세변째 예제에서는 JPA에 Aggregate의 상태 정보와 Event Store에 Domain Event를 저장하는 예시이다.

### Example 4
네번째 예제에서는 Command용 저장소와 Query용 저장소를 불리한 CQRS 예제를 다른다.
