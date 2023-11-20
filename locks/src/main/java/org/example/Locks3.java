package org.example;

import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class Locks3 {
    static ZooKeeper zooKeeper;
    static CountDownLatch cc = new CountDownLatch(1);
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        String clientId = UUID.randomUUID().toString();
        String rootNode = "/locks";
        // if the previous client unlocks, the watcher starts
        Watcher watcher = new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if(watchedEvent.getType() == Event.EventType.NodeChildrenChanged){
                    try {
                        cc.countDown();
                        TryLock(clientId, rootNode);
                        TryUnlock(clientId, rootNode);
                    } catch (KeeperException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };

        zooKeeper = new ZooKeeper("localhost:2183", 20000, watcher);
        if(zooKeeper.exists(rootNode, false) ==  null){
            zooKeeper.create(rootNode, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        // Create a lock
        zooKeeper.create(rootNode+"/"+"lock-", clientId.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        TryLock(clientId, rootNode);
        TryUnlock(clientId, rootNode);

        Thread.sleep(100_100_100);
    }

    public static boolean OwnTheLock(String clientId, String rootNode) throws InterruptedException, KeeperException{
        // find the smallest lock
        List<String> lockId = zooKeeper.getChildren(rootNode, false);
        lockId.sort(String::compareTo);
        byte[] data = zooKeeper.getData(rootNode+"/"+lockId.get(0), false, null);

        if (data != null && new String(data).equalsIgnoreCase(clientId)){
            return true;
        }
        else return false;
    }

    public static boolean TryLock(String clientId, String rootNode) throws InterruptedException, KeeperException{
        // Try to get the lock
        if (OwnTheLock(clientId, rootNode)){
            System.out.println("Get a lock successfully! The expiration time is 30 seconds");
            for (int i=0;i<30;i++){
                System.out.println("lock for "+ i + "seconds");
                Thread.sleep(1000);
            }
            System.out.println("Time out!");
            return true;
        }
        else{
            System.out.println("Can't get the lock. Waiting...");
            zooKeeper.getChildren(rootNode, true);
            cc.await();
            return true;
        }
    }

    public static boolean TryUnlock(String clientId, String rootNode) throws KeeperException, InterruptedException {
        // check the owner of the lock
        List<String> lockId = zooKeeper.getChildren(rootNode, false);
        lockId.sort(String::compareTo);
        byte[] data = zooKeeper.getData(rootNode+"/"+lockId.get(0), false, null);

        if (data != null && new String(data).equalsIgnoreCase(clientId)){
            zooKeeper.delete(rootNode+"/"+lockId.get(0), -1);
            System.out.println("Unlock!");
            return true;
        }
        else return false;
    }
}
