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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 * [SSSSS-核心类]
 *
 * 数据传输流，表示一个连接，可以理解为每一个请求，就是一个Channel;
 *
 * 一个I/O操作的对象，所有的Inbound（入站）事件都是由Channel产生，Outbound（出站）事件最终都会有Channel处理；
 * 每一个新创建的Channel，都会分配一个新的ChannelPipeline，
 * 这个关联是永久性的，Channel既不能附上另一个ChannelPipeline，也不能分离当前的ChannelPipeline；这是Netty固定的组件的生命周期。
 *
 * 一个Channel创建之后，首先要做的事情就是向Pipeline中添加Handler，然后才是将它注册到NioEventLoopGroup中；
 * 这个顺序不能错，否则，handler的handlerAdded，channelRegistered和channelActive将不会被调用。
 * 当NioServerSocketChannel收到一个连接时，ServerHandler的的channelRead方法将会被调用，新建好的连接当成参数传递进来；
 *
 *
 * ------------------- Channel 的四个状态 -------------------
 * channelUnregistered      channel 创建但未注册到一个 EventLoop.
 * channelRegistered        channel 注册到一个 EventLoop.
 * channelActive            channel 的活动的(连接到了它的 remote peer（远程对等方）)，现在可以接收和发送数据了
 * channelInactive          channel 没有连接到 remote peer（远程对等方）
 *
 * ------------------- Channel 的生命周期 -------------------
 * Channel 的正常的生命周期图，
 * 当这些状态变化出现，对应的事件将会生成，这样与 ChannelPipeline 中的 ChannelHandler 的交互就能及时响应
 * <pre>
 * +---------------------+             +---------------------+
 * |  channelRegistered  | ----------> |     channelActive   |
 * |---------------------+             +---------------------+
 *                                                |
 *                                                |
 * +---------------------+             +----------▼---------+
 * | channelUnregistered | <---------- |    channelInactive  |
 * +---------------------+             +---------------------+
 *
 * ------------------- Channel 最终派生实现类 -------------------
 * tcp server - NioServerSocketChannel
 * tcp client - NioSocketChannel
 * upd socket - NioDatagramChannel
 *
 * ------------------- io.netty.channel.Channel -------------------
 * Channel内部定义了一个Unsafe类型，Channel定义了对外提供的方法，Unsafe定义了具体实现。
 * Channel定义的的方法分为三种类型:
 *  1) 辅助方法；
 *  2) inbound 方法；
 *  3) outbound 方法；
 *
 * -------------------------- NIO SelectionKey ---------------------------------
 * NIO SelectionKey 四种事件：
 *  1) {@link java.nio.channels.SelectionKey#OP_ACCEPT 服务端关注此类型的事件，例如：netty ioServerSocketChannel
 *         接收连接继续事件，表示服务端监听到了客户端的连接，服务端可以接收此连接了；
 *     }
 *  2) {@link java.nio.channels.SelectionKey#OP_CONNECT
 *         连接就绪事件，表示客户端与服务端的连接已经建立成功；
 *     }
 *  3) {@link java.nio.channels.SelectionKey#OP_READ 客户端关注此类型事件，例如：netty NioSocketChannel
 *         读就绪事件，表示通道中已经有了可读的数据，可以执行读操作了；
 *         当向通道中注册SelectionKey.OP_READ事件后，若客户端有向缓存中write数据，下次轮询时，则 isReadable()=true；
 *     }
 *  4) {@link java.nio.channels.SelectionKey#OP_WRITE
 *         写就绪事件，表示已经可以向通道写数据了；
 *         当向通道中注册SelectionKey.OP_WRITE事件后，则当前轮询线程中isWritable()一直为ture，如果不设置为其他事件；
 *     }
 *
 * </pre>
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the {@linkplain ChannelConfig configuration parameters} of the channel (e.g. receive buffer size),</li>
 * <li>the I/O operations that the channel supports (e.g. read, write, connect, and bind), and</li>
 * <li>the {@link ChannelPipeline} which handles all I/O events and requests
 *     associated with the channel.</li>w
 * </ul>
 *
 * <h3>All I/O operations are asynchronous.</h3>
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which will notify you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * <h3>Channels are hierarchical</h3>
 * <p>
 * A {@link Channel} can have a {@linkplain #parent() parent} depending on
 * how it was created.  For instance, a {@link SocketChannel}, that was accepted
 * by {@link ServerSocketChannel}, will return the {@link ServerSocketChannel}
 * as its parent on {@link #parent()}.
 * <p>
 * The semantics of the hierarchical structure depends on the transport
 * implementation where the {@link Channel} belongs to.  For example, you could
 * write a new {@link Channel} implementation that creates the sub-channels that
 * share one socket connection, as <a href="http://beepcore.org/">BEEP</a> and
 * <a href="https://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 *
 * <h3>Downcast to access transport-specific operations</h3>
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *
 * <h3>Release resources</h3>
 * <p>
 * It is important to call {@link #close()} or {@link #close(ChannelPromise)} to release all
 * resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * Returns the globally unique identifier of this {@link Channel}.
     */
    ChannelId id();

    /**
     * 获取EventLoop实例，每个Channel实例都会被注册到一个EventLoop中，这个EventLoop实例就是Channel注册的实例。
     *
     * Return the {@link EventLoop} this {@link Channel} was registered to.
     */
    EventLoop eventLoop();

    /**
     * 获取父Channel实例。如: channelB = channelA.accept(), 那么channelA就是channelB的parent。
     *
     * Returns the parent of this channel.
     *
     * @return the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     */
    Channel parent();

    /**
     * 获取Channel实例的配置信息。
     *
     * Returns the configuration of this channel.
     */
    ChannelConfig config();

    /**
     * channel是否处于open状态。
     * netty为每个channel定义了四种状态open->registered->active->closed。
     * 一个新创建的channel处于open状态，随后他被注册到一个eventloop中它处于open+registered状态，
     * 当这个channel上的连接建立成功后它处于open+registered+active状态，被关闭后处于closed状态。
     *
     * Returns {@code true} if the {@link Channel} is open and may get active later
     */
    boolean isOpen();

    /**
     * channel是否处于registered状态。
     *
     * Returns {@code true} if the {@link Channel} is registered with an {@link EventLoop}.
     */
    boolean isRegistered();

    /**
     * 返回Channel是否激活，已激活说明与远程连接对等
     * Return {@code true} if the {@link Channel} is active and so connected.
     */
    boolean isActive();

    /**
     * Return the {@link ChannelMetadata} of the {@link Channel} which describe the nature of the {@link Channel}.
     */
    ChannelMetadata metadata();

    /**
     * 返回已绑定的本地SocketAddress
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress localAddress();

    /**
     * 返回已绑定的远程SocketAddressw
     * Returns the remote address where this channel is connected to.  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the remote address of this channel.
     *         {@code null} if this channel is not connected.
     *         If this channel is not connected but it can receive messages
     *         from arbitrary remote addresses (e.g. {@link DatagramChannel},
     *         use {@link DatagramPacket#recipient()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     */
    SocketAddress remoteAddress();

    /**
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     */
    ChannelFuture closeFuture();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     */
    boolean isWritable();

    /**
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     */
    long bytesBeforeUnwritable();

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     */
    long bytesBeforeWritable();

    /**
     * 得到channel内部的Unsafe实例。
     *
     * Returns an <em>internal-use-only</em> object that provides unsafe operations.
     */
    Unsafe unsafe();

    /**
     * 返回分配给Channel的ChannelPipeline
     * Return the assigned {@link ChannelPipeline}.
     */
    ChannelPipeline pipeline();

    /**
     * channel持有的buffer分配器（浅拷贝，不共享readerIndex和writeIndex，且不会导致引用计数+1）；
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * 从channel中读取数据，把数据放到输入缓冲区中，
     * 然后触发{@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)} 和
     *        {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext)} 调用，
     * 如果之前已经有一个read操作正在执行或等待执行，此方法不会有任何影响。
     *
     * @return
     */
    @Override
    Channel read();

    @Override
    Channel flush();

    /**
     * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
     * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
     * following methods:
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(EventLoop, ChannelPromise)}</li>
     *   <li>{@link #deregister(ChannelPromise)}</li>
     *   <li>{@link #voidPromise()}</li>
     * </ul>
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

        /**
         * 参考 {@link Channel#localAddress()}
         * Return the {@link SocketAddress} to which is bound local or
         * {@code null} if none.
         */
        SocketAddress localAddress();

        /**
         * 参考 {@link Channel#remoteAddress()}
         * Return the {@link SocketAddress} to which is bound remote or
         * {@code null} if none is bound yet.
         */
        SocketAddress remoteAddress();

        /**
         * 注册 register语义：
         *  1) 将Channel和EventLoop绑定，EventLoop线程就是I/O线程；
         *  2) 确保真正的register（{@link io.netty.channel.AbstractChannel.AbstractUnsafe#doRegister() 子类实现内容}）操作在I/O线程中执行；
         *  3) 确保每个register的操作仅执行一次；
         *  4) 真正的register操作执行成功后，触发channelRegistered事件，
         *     若channel此时仍处于active状态，触发channelActive事件，并确保这些事件只触发一次；
         *  5) 真正的register操作执行成功后,
         *     若 channel此时仍处于active状态，并且channel的配置支持autoRead,
         *     则执行beginRead操作，让eventLoop可以自动触发channel的read事件；
         * Register the {@link Channel} of the {@link ChannelPromise} and notify
         * the {@link ChannelFuture} once the registration was complete.
         */
        void register(EventLoop eventLoop, ChannelPromise promise);

        /**
         * 参考 {@link Channel#bind(SocketAddress)}，必须在I/O线程中执行；
         * Bind the {@link SocketAddress} to the {@link Channel} of the {@link ChannelPromise} and notify
         * it once its done.
         */
        void bind(SocketAddress localAddress, ChannelPromise promise);

        /**
         * 参考 {@link Channel#connect(SocketAddress)}，必须在I/O线程中执行；
         *
         * Connect the {@link Channel} of the given {@link ChannelFuture} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * The {@link ChannelPromise} will get notified once the connect operation was complete.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        /**
         * 参考 {@link Channel#disconnect()}，必须在I/O线程中执行；
         *
         * Disconnect the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void disconnect(ChannelPromise promise);

        /**
         * 参考 {@link Channel#close()}，必须在I/O线程中执行；
         *
         * Close the {@link Channel} of the {@link ChannelPromise} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void close(ChannelPromise promise);

        /**
         * 立即关闭channel，并且不触发任何事件；
         *
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * 参考 {@link Channel#deregister()}，必须在I/O线程中执行；
         *
         * Deregister the {@link Channel} of the {@link ChannelPromise} from {@link EventLoop} and notify the
         * {@link ChannelPromise} once the operation was complete.
         */
        void deregister(ChannelPromise promise);

        /**
         * 为channel触发read事件做准备（例如：把read事件注册到NIO 的selector上），必须在I/O线程中执行；
         *
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
         * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * 参考 {@link Channel#write(Object)}，必须在I/O线程中执行；
         *
         * 写数据到远程客户端，数据通过ChannelPipeline传输过去
         * Schedules a write operation.
         */
        void write(Object msg, ChannelPromise promise);

        /**
         * 参考 {@link Channel#flush()}，必须在I/O线程中执行；
         *
         * 刷新先前的数据
         * Flush out all write operations scheduled via {@link #write(Object, ChannelPromise)}.
         */
        void flush();

        /**
         * 获得一个特殊的ChannelPromise，它可被重用，并传递给{@link Unsafe}操作，
         * 它永远不会收到成功或者错误的通知，因为它只是一个操作的占位符。
         * Return a special ChannelPromise which can be reused and passed to the operations in {@link Unsafe}.
         * It will never be notified of a success or error and so is only a placeholder for operations
         * that take a {@link ChannelPromise} as argument but for which you not want to get notified.
         */
        ChannelPromise voidPromise();

        /**
         * 获得channel的输出缓冲区，write的数据就是追加到这个缓冲区中；必须在I/O线程中执行；
         *
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
