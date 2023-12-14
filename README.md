# Report

## 代码原理

集群由三个服务器组成，每个client分别连接对应服务器，它们需要共同访问一个资源，因此需要使用锁机制赋予权限。每个client提出请求后server会为该client设置一个键值对节点存储在`/locks`下，其中key为lock编号，value为client对应的UUID。这些键值对内容将自动在所有服务器中同步，所有client都可以读取到。`/locks`中最小的节点对应的client将获得锁，其余client将等待（运用了java的阻塞线程机制和zookeeper的watch机制），直到30s后锁释放，对应的键值对节点将删除，watch机制监测到`/locks`发生变动后，其余被阻塞的client被唤醒，此时最小值节点对应的client将获得锁，以此循环。

## 代码结构

代码的主要逻辑为：

````java
CreateLock();

// Try to get the lock
if(OwnTheLock){
	TryLock();
}
else{
	TryLock(watcher);
}

// Try to unlock
if(OwnTheLock){
    TryUnlock();
}
````

## 操作步骤

在zookeeper中启动三个服务器（端口分别为2181,、2182、2183）后，分别打开三个终端运行（Locks1, Locks2, Locks3），这些client分别连接三个不同的服务器。

进入代码文件`/locks`目录，运行以下命令

```bash
mvn exec:java -Dexec.mainClass=org.example.Locks1
```

可以看到服务器`/locks`中有三个节点产生，每个节点的值为各个client对应的UUID：

![](https://notes.sjtu.edu.cn/uploads/upload_8b6972da973e5acb43a9234ba0059e07.png)


由于Locks1最小，client1优先获得锁：

![](https://notes.sjtu.edu.cn/uploads/upload_df18c3cc7679e1c55f2e2828bea1d8a8.png)


30秒后client1释放锁，client2获得锁，以此类推。

![](https://notes.sjtu.edu.cn/uploads/upload_74cb968eb804b6d9e3e3262acfb99c75.png)


![](https://notes.sjtu.edu.cn/uploads/upload_9f7fa3621840d64a2d2258de17cbe3bd.png)


## 改进

在获取锁和解锁的过程中，由于要索引当前锁的最小值，需要进行服务器间较多的读写操作，较为费时，可能无法严格保证操作的原子性。

## Reference

https://www.youtube.com/watch?v=a-zatYN1Lx0
