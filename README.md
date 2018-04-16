# CQRS-ESwithAxon

Axon Framework은 Event-Driven lightweight CQRS framework으로 Aggregate의 상태정보를 저장하는 방법과 EventSourcing을 지원한다.

![Architecture overview of a CQRS Application](https://blobscdn.gitbook.com/v0/b/gitbook-28427.appspot.com/o/assets%2F-L9ehuf5wnTxVIo9Rle6%2F-L9ei79JpweCtX6Qur65%2F-L9eiEg8i2dLcK2ovEZU%2Fdetailed-architecture-overview.png?generation=1523282680564557&alt=media)

Axon framework을 이용하기 위해서는 Maven dependency를 추가하면 된다.

 - core Axon's core code
 - amqp Use the MQ of the AMQP protocol, such as rabbit, to implement the distribution of events across JVMs
 - distributed-commandbus-jgroups Use Jgroup to implement Command distribution across JVM
 - distributed-commandbus-springcloud Integration with Spring Cloud, Command Discovery across JVM using DiscoveryClient and RESTemplate metrics Provide monitoring related information
 - mongo Axon and mongoDB integration
 - spring-boot-autoconfigure To implement Spring's autoconfigure support, you only need to provide related properties to automatically configure Axon
 - spring-boot-starter-jgroups Use distributed-commandbus-jgroups with spring autoconfigure to provide jgroup "one-click" integration
 - spring-boot-starter Integrate with springboot
 - spring Provide various annotations, integrated with spring
