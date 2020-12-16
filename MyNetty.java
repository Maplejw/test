package idatacenter.report.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MyNetty {

    public static void main(String[] args) throws IOException {
        Reactor reactor = new Reactor();
        ExecutorService exe = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(128),new NettyThreadFactory("reactor-http-loop-"));
        exe.execute(reactor);
    }
    
    public static class NettyThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private String namePrefix = "netty-work-thread-";

        public NettyThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
        }
        
        public NettyThreadFactory(String namePrefix) {
            this();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
    
    
    public static class SubReactor implements Runnable {
        private Selector selector;
        
        public SubReactor(Selector selector) {
            this.selector = selector;
        }
        
        @Override
        public void run() {
            try {
                while(true) {
                    int opt = selector.selectNow();
                    if(opt == 0) continue;
                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> it = selected.iterator();
                    while (it.hasNext()) {
                        SelectionKey selectionKey = it.next();
                        System.out.println(Thread.currentThread().getName() + ":coming");
                        it.remove();
                        Runnable r = (Runnable) selectionKey.attachment();
                        r.run();
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static class Reactor implements Runnable {
        private Selector[] selector = new Selector[2];
        private ExecutorService pool;

        public Reactor() throws IOException {
            selector[0] = Selector.open();
            selector[1] = Selector.open();
            ServerSocketChannel socket = ServerSocketChannel.open();
            socket.socket().bind(new InetSocketAddress(8888));
            socket.configureBlocking(false);// 设置为非阻塞，这样accept会立马返回结果，如果没有连接则是null
            SelectionKey sk = socket.register(selector[0], SelectionKey.OP_ACCEPT);
            sk.attach(new Acceptor(socket));
            pool = new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256),new NettyThreadFactory("reactor-work-loop-"));
            
            SubReactor subReactor = new SubReactor(selector[1]);
            ExecutorService exe = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1),new NettyThreadFactory("reactor-http-subloop-"));
            exe.execute(subReactor);
        }

        public void run() {
            try {
                while (true) {
                    int opt = selector[0].selectNow();
                    if(opt == 0) continue;
                    Set<SelectionKey> selected = selector[0].selectedKeys();
                    Iterator<SelectionKey> it = selected.iterator();
                    while (it.hasNext()) {
                        System.out.println(Thread.currentThread().getName() + ":coming");
                        SelectionKey selectionKey = it.next();
                        it.remove();
                        Runnable r = (Runnable) selectionKey.attachment();
                        r.run();
                    }
                }
            } catch (IOException e) {
            }
        }

        class Acceptor implements Runnable {
            private ServerSocketChannel serverSocket;

            public Acceptor(ServerSocketChannel serverSocket) {
                this.serverSocket = serverSocket;
            }

            public void run() {
                // nio情况下此方法是非阻塞的，如果没有连接进来，会立马返回null
                try {
                    SocketChannel channel = serverSocket.accept();
                    if (channel != null) {
                        System.out.println(Thread.currentThread().getName() + ":accept");
                        new Handler(selector[1], channel);
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        class Handler implements Runnable {
            private Selector selector;
            private SocketChannel socketChannel;
            private static final int READING = 0;
            private static final int SENDING = 1;
            private int status = READING;
            private SelectionKey sk;

            public Handler(Selector selector, SocketChannel socketChannel) throws IOException {
                this.selector = selector;
                this.socketChannel = socketChannel;
                this.socketChannel.configureBlocking(false);
                sk = this.socketChannel.register(this.selector, SelectionKey.OP_READ);
                sk.attach(this);
            }

            public void run() {
                if (status == READING) {
                    read();
                } else {
                    try {
                        send();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            private void send() throws IOException {
                ByteBuffer writeBuffer = ByteBuffer.allocate(4096);
                writeBuffer.put("hello world".getBytes());
                writeBuffer.flip();
                try {
                    System.out.println(Thread.currentThread().getName() + ":sending");
                    socketChannel.write(writeBuffer);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } finally {
                    socketChannel.close();
                }
            }

            private void read() {
                try {
                    System.out.println(Thread.currentThread().getName() + ":reading");
                    ByteBuffer readBuffer = ByteBuffer.allocate(4096);
                    socketChannel.read(readBuffer);
                    pool.execute(() -> {
                        byte[] buff = readBuffer.array();
                        System.out.println(Thread.currentThread().getName() + ":received:" + new String(buff));
                        status = SENDING;
                        sk.interestOps(SelectionKey.OP_WRITE);
                    });
                } catch (ClosedChannelException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        }
    }

}
