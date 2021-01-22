/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {
    @SuppressWarnings("unchecked")
    static final Map.Entry<ChannelOption<?>, Object>[] EMPTY_OPTION_ARRAY = new Map.Entry[0];
    @SuppressWarnings("unchecked")
    static final Map.Entry<AttributeKey<?>, Object>[] EMPTY_ATTRIBUTE_ARRAY = new Map.Entry[0];

    volatile EventLoopGroup group;
    @SuppressWarnings("deprecation")
    private volatile ChannelFactory<? extends C> channelFactory;
    private volatile SocketAddress localAddress;

    // The order in which ChannelOptions are applied is important they may depend on each other for validation
    // purposes.
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<AttributeKey<?>, Object>();
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        attrs.putAll(bootstrap.attrs);
    }

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-created
     * {@link Channel}
     */
    public B group(EventLoopGroup group) {
        ObjectUtil.checkNotNull(group, "group");
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * 设置Channel的实现类的Class（tcp-client，tcp-server，udp-socket）；
     * 此处不创建Channel的实例，实例的创建使用Java反射{@link io.netty.channel.ChannelFactory#newChannel()}  工厂模式}方法；
     *
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(io.netty.channel.ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public B channel(Class<? extends C> channelClass) {
        return channelFactory(new ReflectiveChannelFactory<C>(
                ObjectUtil.checkNotNull(channelClass, "channelClass")
        ));
    }

    /**
     * @deprecated Use {@link #channelFactory(io.netty.channel.ChannelFactory)} instead.
     */
    @Deprecated
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        ObjectUtil.checkNotNull(channelFactory, "channelFactory");
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }

    /**
     * The {@link SocketAddress} which is used to bind the local "end" to.
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> B option(ChannelOption<T> option, T value) {
        ObjectUtil.checkNotNull(option, "option");
        synchronized (options) {
            if (value == null) {
                options.remove(option);
            } else {
                options.put(option, value);
            }
        }
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        ObjectUtil.checkNotNull(key, "key");
        if (value == null) {
            attrs.remove(key);
        } else {
            attrs.put(key, value);
        }
        return self();
    }

    /**
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration.  This method is useful when making
     * multiple {@link Channel}s with similar settings.  Please note that this method does not clone the
     * {@link EventLoopGroup} deeply but shallowly, making the group a shared resource.
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        return doBind(ObjectUtil.checkNotNull(localAddress, "localAddress"));
    }

    /********
     * [SSS-操作入口：bind] ServerBootstrap - ServerSocketChannel - bind(...)
     * 执行pipeline - bind 
     * 源码解读 {@link io.netty.channel.DefaultChannelPipeline#bind(SocketAddress, ChannelPromise)
     *      a) 因bind操作是Outbound类型，所有从tail-head，执行bind操作；
     *      b) 最后有head的bind操作，调用AbstractUnsafe#bind，进行JDK 底层的SocketChannel#bind操作；
     * }
     */
    private ChannelFuture doBind(final SocketAddress localAddress) {
        // 已完成 register 操作，若异常不是Null，则返回错误；
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        if (regFuture.isDone()) {
            // register 注册动作已经完成并成功，则执行bind操作
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }

    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            /*********
             * 构造Channel实例，同时也会构造ChannelPipeline实例（head + tail实例也将创建出来，并且连接起来）；
             * 源码解读：{@link NioSocketChannel#NioSocketChannel() tcp-client 默认构造器
             *      1) 绑定Java JDK的SocketChannel实例；
             *      2) 创建Unsafe(NioSocketChannelUnsafe)，ChannelPipeline，NioSocketChannelConfig实例等；
             *      3) 设置channel非阻塞模式，客户端：SelectionKey.OP_READ 事件（等待读服务端返回数据）；
             * }
             * 源码解读：{@link NioServerSocketChannel#NioServerSocketChannel() tcp-server 默认构造器
             *      1) 绑定Java JDK的ServerSocketChannel实例；
             *      2) 创建Unsafe(NioMessageUnsafe)，ChannelPipeline，NioServerSocketChannelConfig实例等；
             *      3) 设置channel非阻塞模式，服务端：SelectionKey.OP_ACCEPT 事件（接收连接继续事件）
             * }
             * 源码解读：{@link io.netty.channel.DefaultChannelPipeline#DefaultChannelPipeline(Channel)
             *      1) 创建Channel双向链表结构的Tail（ChannelInboundHandler）节点；
             *      2) 创建Channel双向链表结构的Head（ChannelInboundHandler，ChannelOutboundHandler）节点；
             *      3) 连接起来 Head + Tail（head.text = tail, tail.prev = head）；
             * }
             */
            channel = channelFactory.newChannel();
            /*********
             * 源码解读：{@link Bootstrap#init(Channel) 客户端
             *      1）向pipeline中添加handler（head + tail）；
             * }
             * 源码解读：{@link ServerBootstrap#init(Channel) 服务端
             *      1）向pipeline中添加handler以及一个ChannelInitializer实例（head + channelInitializer + tail）；
             * }
             */
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly();
                // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        /*********
         * 1) 获得NioEventLoopGroup实例；
         * 2) 创建一个ChannelPromise实例，关联Channel，Channel持有Unsafe实例，
         *    通过调用 Unsafe#register(...)方法完成绑定一个线程NioEventLoop实例；
         *    源码解读 {@link io.netty.channel.AbstractChannel#register(Channel)
         *         a) 根据ChooseFactory设置的策略，选择线程池中的一个线程（NioEventLoop实例；
         *            源码解读：参考 {@link io.netty.util.concurrent.MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor, EventExecutorChooserFactory, Object...)}
         *         b) 通过NioEventLoop实例调用register(channel)设置提交任务的异步操作；
         *            源码解读：{@link io.netty.channel.SingleThreadEventLoop#register(Channel)
         *                  ChannelPromise 关联Channel，Channel 持有 Unsafe的实例，register的操作封装在Unsafe中，因此：
         *                  源码解读 {@link io.netty.channel.AbstractChannel.AbstractUnsafe#register(EventLoop, ChannelPromise)
         *                       1⃣) Channel绑定一个NioEventLoop的实例，但是一个NioEventLoop实例可能会被多个Channel绑定；
         *                       2⃣) 此后Channel提交的任务由NioEventLoop调度（即 Executor.execute(Runnable)）异步操作；
         *                  }
         *            }
         *    }
         * 3) 提交task给EventLoop，由eventLoop中的线程（NioEventLoop#execute(Runnable)）负责调用register0(promise)
         *    源码解读 {@link io.netty.util.concurrent.SingleThreadEventExecutor#execute(Runnable)
         *         a) 检查execute任务的线程是不是NioEventLoop的内部线程；
         *         b) 将task提交到taskQueue（LinkedBlockingQueue<Runnable>(maxPendingTasks)）
         *         c) 若不是NioEventLoop的内部线程，则通过持有executor（ThreadPerTaskExecutor）实例创建一个新的线程；
         *         d) 将新创建的线程绑定到NioEventLoop的内部线程，执行eventLoop#run() 不断地循环获取新的任务；
         *    }
         * 4) EventLoop#run() 功能与JDK线程池Worker那样，不断地做Selector#select操作和轮询taskQueue队列；
         *    源码解读 {@link io.netty.channel.nio.NioEventLoop#run()
         *         a) 校验taskQueue是否堆积任务，若不为空则执行一次Selector#selectNow()，非阻塞，即刻返回就绪事件数量一个或多个；
         *         b) 若为空，则根据selectStrategy的结果集（即SelectStrategy.SELECT），
         *            进入SelectStrategy.SELECT条件分支，执行Selector#select(timeoutMillis)，
         *            即selectNow()的阻塞超时方法，有事件立即返回，否则阻塞到timeoutMillis时间；
         *         c) 根据eventLoop#ioRatio控制的时间比重："[0 ~ 100)"，
         *            若是100%，则先执行IO操作，然后再执行taskQueue队列中的任务；
         *            若不是100，那么先执行IO操作，然后再执行taskQueue队列中的任务，但是必须控制执行taskQueue任务的总时间；
         *            非IO操作的时间根据IO耗时和ioRatio比例计算（ioTime * (100 - ioRatio) / ioRatio）的结果获得；
         *
         *            源码解读 - 执行IO操作{@link NioEventLoop#processSelectedKeys()
         *            }
         *            源码解读 - 执行taskQueue队列中的任务{@link NioEventLoop#runAllTasks()
         *            }
         *            源码解读 - 执行taskQueue队列中的任务，但控制任务的总时间{@link NioEventLoop#runAllTasks(long)
         *            }
         *    }
         * 5) NioEventLoop线程轮询taskQueue队列，执行register任务
         *    源码解读 {@link io.netty.channel.AbstractChannel.AbstractUnsafe#register0(ChannelPromise)
         *         a) 执行真正的注册操作：将JDK底层的Channel 注册到 Selector，交由子类实现；
         *            源码解读 {@link AbstractNioChannel#doRegister() JDK register操作
         *                 将Java JDK SocketChannel(or ServerSocketChannel)注册到Selector（多路复用器）中，
         *                 但是在此处的监听集合设置是：0，也就是意味着什么都不监听（也就表示后续会有地方修改selectionKey的监听集合）；
         *            }
         *         b) 通过pipeline关联的Channel，然后在使用channel获取NioEventLoop实例，检测NioEventLoop线程是否启动；
         *            然后由 pipeline#execute 触发：ChannelHandler#handlerAdded方法；
         *            源码解读 {@link io.netty.channel.ChannelInitializer#initChannel(ChannelHandlerContext)
         *            }
         *    }
         * 6) 从pipeline 链表的Head开始至Tail结束，依次往下寻找所有的inbound handler，执行其 channelRegistered(ctx)操作；
         *    源码解读 {@link io.netty.channel.AbstractChannelHandlerContext#invokeChannelRegistered()
         *         1) 通过pipeline.fireChannelRegistered() 将 channelRegistered事件抛到pipeline中，pipeline的handlers准备处理此事件；
         *         2) 然后在一个handler处理完成之后，通过 context.fireChannelRegistered() 向后传播给下一个 handler；
         *    }
         */
        ChannelFuture regFuture = config().group().register(channel);

        // 执行 register 出现错误
        if (regFuture.cause() != null) {
            // 关闭channel
            if (channel.isRegistered()) {
                channel.close();
            } else {
                // 立即关闭channel，并且不触发任何事件
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.
        /*******
         * 此处表示已可进行 bind() or connect()，以下情况说明：
         * 1) 假如 register 动作是在 eventLoop 中发起的，那么在到达此处代码的时候 register 一定已经完成；
         * 2) 假如 register 任务已提交到 eventLoop中（即已提交到 eventLoop中的taskQueue中），
         *    由于后续的 bind or connect 也一定会进入到同一个 eventLoop 的 queue中，
         *    因此一定是先执行 register成功，才会执行 bind() or connect() 操作；
         */
        return regFuture;
    }

    abstract void init(Channel channel) throws Exception;

    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    public B handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    /**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * Returns the {@link AbstractBootstrapConfig} object that can be used to obtain the current config
     * of the bootstrap.
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    final Map.Entry<ChannelOption<?>, Object>[] newOptionsArray() {
        synchronized (options) {
            return options.entrySet().toArray(EMPTY_OPTION_ARRAY);
        }
    }

    final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    @SuppressWarnings("deprecation")
    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final Map<ChannelOption<?>, Object> options() {
        synchronized (options) {
            return copiedMap(options);
        }
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return copiedMap(attrs);
    }

    static <K, V> Map<K, V> copiedMap(Map<K, V> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<K, V>(map));
    }

    static void setAttributes(Channel channel, Map.Entry<AttributeKey<?>, Object>[] attrs) {
        for (Map.Entry<AttributeKey<?>, Object> e: attrs) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }

    static void setChannelOptions(
            Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('(').append(config()).append(')');
        return buf.toString();
    }

    static final class PendingRegistrationPromise extends DefaultChannelPromise {

        // Is set to the correct EventExecutor once the registration was successful. Otherwise it will
        // stay null and so the GlobalEventExecutor.INSTANCE will be used for notifications.
        private volatile boolean registered;

        PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        void registered() {
            registered = true;
        }

        @Override
        protected EventExecutor executor() {
            if (registered) {
                // If the registration was a success executor is set.
                //
                // See https://github.com/netty/netty/issues/2586
                return super.executor();
            }
            // The registration failed so we can only use the GlobalEventExecutor as last resort to notify.
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
