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
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.nio.channels.Channels;

/**
 * 用于传输业务数据
 *
 * 一个双向链表的数据结构，它的多个实例组成了一个双向链表，并由ChannelPipeline负责维护；
 * 在ChannelPipeline的默认实现中，链表默认添加了Head和Tail节点，
 * 1) Head节点 同时实现{@link ChannelInboundHandler 入站接口} 和 {@link ChannelOutboundHandler 出站接口}，
 *    Head节点 比较特殊，它会最终将事件叫个Channel处理。
 * 2) Tail节点 实现了{@link ChannelInboundHandler 入站接口}，使用Channel触发的 inbound事件会首先在Tail节点处理；
 *
 * 接口 ChannelHandlerContext 代表 ChannelHandler 和ChannelPipeline 之间的关联；
 * 在ChannelHandler 添加到 ChannelPipeline 时会创建一个实例，
 * 就是接口 ChannelHandlerContext，它代表了 ChannelHandler 和ChannelPipeline 之间的关联。
 * 接口ChannelHandlerContext 主要是对通过同一个 ChannelPipeline 关联的 ChannelHandler 之间的交互进行管理。
 *
 * 注意事项：
 *  1）ChannelHandlerContext 与 ChannelHandler 的关联从不改变，所以缓存它的引用是安全的；
 *  2）ChannelHandlerContext 所包含的事件流比其他类中同样的方法都要短，利用此点可以尽可能高地提高性能。
 *
 *
 * Enables a {@link ChannelHandler} to interact with its {@link ChannelPipeline}
 * and other handlers. Among other things a handler can notify the next {@link ChannelHandler} in the
 * {@link ChannelPipeline} as well as modify the {@link ChannelPipeline} it belongs to dynamically.
 *
 * <h3>Notify</h3>
 *
 * You can notify the closest handler in the same {@link ChannelPipeline} by calling one of the various methods
 * provided here.
 *
 * Please refer to {@link ChannelPipeline} to understand how an event flows.
 *
 * <h3>Modifying a pipeline</h3>
 *
 * You can get the {@link ChannelPipeline} your handler belongs to by calling
 * {@link #pipeline()}.  A non-trivial application could insert, remove, or
 * replace handlers in the pipeline dynamically at runtime.
 *
 * <h3>Retrieving for later use</h3>
 *
 * You can keep the {@link ChannelHandlerContext} for later use, such as
 * triggering an event outside the handler methods, even from a different thread.
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>Storing stateful information</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a {@link ChannelHandler} / {@link Channel} and its
 * context. Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * <h3>A handler can have more than one {@link ChannelHandlerContext}</h3>
 *
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * the single instance can be invoked with different
 * {@link ChannelHandlerContext}s if it is added to one or more {@link ChannelPipeline}s more than once.
 * Also note that a {@link ChannelHandler} that is supposed to be added to multiple {@link ChannelPipeline}s should
 * be marked as {@link io.netty.channel.ChannelHandler.Sharable}.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * Return the {@link Channel} which is bound to the {@link ChannelHandlerContext}.
     */
    Channel channel();

    /**
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * The unique name of the {@link ChannelHandlerContext}.The name was used when then {@link ChannelHandler}
     * was added to the {@link ChannelPipeline}. This name can also be used to access the registered
     * {@link ChannelHandler} from the {@link ChannelPipeline}.
     */
    String name();

    /**
     * The {@link ChannelHandler} that is bound this {@link ChannelHandlerContext}.
     */
    ChannelHandler handler();

    /**
     * Return {@code true} if the {@link ChannelHandler} which belongs to this context was removed
     * from the {@link ChannelPipeline}. Note that this method is only meant to be called from with in the
     * {@link EventLoop}.
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * Return the assigned {@link ChannelPipeline}
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
