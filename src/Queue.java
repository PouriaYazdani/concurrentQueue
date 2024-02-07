//package com.company;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;



public class Queue implements Serializable {
    private final MyLinkedList messages;
    private final int messagesSize;
    private Semaphore consumerLock = new Semaphore(1);
    private Semaphore producerLock = new Semaphore(1);
    private AtomicInteger size;
    private final long messageTTL;// in millis
    private transient final Thread cleanupThread;
    private final int capacity;
    private AtomicInteger currentCapacity = new AtomicInteger(0);
    /**
     * bounded message queue
     */
    public Queue(int messagesSize, long messageTTL,int capacity){
        this.messagesSize = messagesSize;
        this.messages = new MyLinkedList();
        this.size = new AtomicInteger(0);
        this.messageTTL = messageTTL;
        this.capacity = capacity;

        Runnable cleanupTask = () -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    removeExpiredMessages();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        cleanupThread = new Thread(cleanupTask);
        cleanupThread.start();
    }

    /**
     * unbounded message queue
     */
    public Queue(long messageTTL,int capacity){
        this(Integer.MAX_VALUE,messageTTL,capacity);
    }

    private void removeExpiredMessages() {
        try {
            consumerLock.acquire();
            producerLock.acquire();
            System.out.println(Thread.currentThread().getId() + "acquired both locks to perform deletion of expired messages");
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        long currentTime = Instant.now().toEpochMilli();
        while (!isEmpty() && isMessageExpired(messages.getHead().value)) {

            Message m = messages.remove().value;
            System.out.println(m + " removed because existed to long");
            size.decrementAndGet();
        }
        consumerLock.release();
        producerLock.release();
        System.out.println(Thread.currentThread().getId() + "released both locks after performing deletion of expired messages");
    }

    private boolean isMessageExpired(Message message) {
        return (Instant.now().toEpochMilli() - message.getTimestamp()) > messageTTL;
    }


    public void put(Message message){
        if (this.isFull()){
            messages.printList();
            System.out.println(Thread.currentThread().getId()+" was trying to produce but queue was full and returned" +
                    " immediately, limit was " + messagesSize);
            return;
        }
        System.out.println(Thread.currentThread().getId() + "is trying to get producer lock");
        try {
            producerLock.acquire();
            if (capacityExceeded()){
                System.out.println(Thread.currentThread().getId() + "acquired producer lock but capacity limit was exceeded(" +
                       +currentCapacity.intValue()+ "), so returned");
                producerLock.release();
                return;
            }else
                System.out.println(Thread.currentThread().getId() + "acquired the  producer lock");
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        //maybe the queue got full in this time interval
        System.out.println(Thread.currentThread().getId() + "could wait until the queue is not full");
        while (isFull());
        System.out.println(Thread.currentThread().getId() + "ready to produce");
        messages.add(message);
        size.incrementAndGet();
        currentCapacity.addAndGet(message.getContent().length());
        System.out.println(Thread.currentThread().getId() + "produced the message" + message);
        System.out.println(Thread.currentThread().getId() + "released the producer lock");
        producerLock.release();
    }

    public Optional<Message> take(){
        System.out.println(Thread.currentThread().getId() + "is trying to acquire the consumer lock");
        try {
            consumerLock.acquire();
            System.out.println(Thread.currentThread().getId() + "acquired the consumer lock");

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        //maybe the queue got empty in this time interval
        System.out.println(Thread.currentThread().getId() + "could wait until the queue is not empty");
        while (isEmpty());
        System.out.println(Thread.currentThread().getId() + "ready to consume");
        Optional<Message> message = Optional.of(messages.remove().value);
        size.decrementAndGet();
        currentCapacity.addAndGet(-message.get().getContent().length());
        System.out.println(Thread.currentThread().getId() + "consumed the message " + message.get());
        consumerLock.release();
        System.out.println(Thread.currentThread().getId() + "released the consumer lock");
        return message;
    }
    public Optional<Message> takeNonBlocking() throws InterruptedException {

        if (this.size.compareAndSet(0,0)){
            System.out.println(Thread.currentThread().getId()+" was trying to consume but queue was empty and returned immediately");
            return Optional.empty();
        }
        return this.take();
    }


    private boolean capacityExceeded(){
        return currentCapacity.intValue() > capacity;
    }
    private boolean isFull(){
        return size.compareAndSet(messagesSize,messagesSize);
    }
    public boolean isEmpty(){
        return size.compareAndSet(0,0);
    }


    private int messagesLength(){
        int length = messages.getHead().value.getContent().length();;
        Node m = messages.getHead().next;
        while (m != null){
            length += m.value.getContent().length();
            m = m.next;
        }
        return length;
    }

    private double memoryAllocated(){
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory())/(1024.0*1024.0);
    }

    public Stats getStats(){

        try {
            consumerLock.acquire();
            producerLock.acquire();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        Stats stat = new Stats(size.intValue(), this.messagesLength(), this.memoryAllocated());
        consumerLock.release();
        producerLock.release();
        System.out.println(stat);
        return stat;
    }

    public void printQueue(){
        messages.printList();
    }

    public void shutdownCleanupThread() {
        cleanupThread.interrupt();
    }

}
class Stats implements Serializable{
    private final int numbersOfMessages;
    private final int messagesLength;
    private final double currentMemoryAllocated;
    public Stats(int numbersOfMessages,int messagesLength, double currentMemoryLength){
        this.numbersOfMessages = numbersOfMessages;
        this.messagesLength = messagesLength;
        this.currentMemoryAllocated = currentMemoryLength;
    }

    public int getNumbersOfMessages() {
        return numbersOfMessages;
    }
    public int getMessagesLength() {
        return messagesLength;
    }
    public double getCurrentMemoryAllocated() {
        return currentMemoryAllocated;
    }

    @Override
    public String toString(){
        return "============================="+"\nnumber of messages: " + numbersOfMessages
                + "\ntotal length: " + messagesLength
                + "\ncurrent memory allocated: " + currentMemoryAllocated+"MB"
                + "\n=============================";
    }
}

class MyLinkedList implements Serializable {
    private Node head;
    public MyLinkedList(){
        this.head = null;
    }

    public void add(Message message){
        Node newNode = new Node(message);
        if (this.head == null){
            head = newNode;
        }
        else {
            Node current  = this.head;
            while (current.next != null){
                current = current.next;
            }
            current.next = newNode;
        }

    }
    // remove method based on message priority
    public Node remove(){
        if (head == null) return null;

        Node temp = head;
        Node highestPriority = head;
        Node highestPriorityPrev = null;

        while (temp != null) {
            if (temp.next != null && temp.next.value.getPriority() > highestPriority.value.getPriority()) {
                highestPriority = temp.next;
                highestPriorityPrev = temp;
            }
            temp = temp.next;
        }

        if (highestPriority == head) {
            // Highest priority node is the first node
            head = head.next;
        } else {
            // Remove highest priority node by skipping it in the list
            if (highestPriorityPrev != null) {
                highestPriorityPrev.next = highestPriority.next;
            }
        }

        return highestPriority;
    }



    public void printList() {
        Node current = head;
        while (current != null) {
            System.out.println(current.value.getContent() + " with priority " + current.value.getPriority());
            current = current.next;
        }
    }

    public Node getHead() {
        return head;
    }
    public void setHead(Node head) {
        this.head = head;
    }
}
class Node implements Serializable{
    Message value;
    Node next;
    public Node(Message value){
        this.value = value;
        this.next = null;
    }

}