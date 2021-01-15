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

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * [SSSSS-核心类] 事件循环
 *
 * Netty使用EventLoop来处理连接上的读写事件，
 * 而一个连接上的所有的请求都保证在一个EventLoop中被处理；
 * 一个EventLoop中只有一个Thread，所以也就实现了一个连接上的所有事件只会在一个线程中被执行。
 *
 * 一个EventLoop将被分配给一个Channel，来负责这个Channel的整个生命周期之内的所有ChannelInboundHandler和ChannelOutboundHandler事件。
 *
 ---------------------------------------------------------------------------------------
 * 其实EventLoop继承了{@link java.util.concurrent.ScheduledExecutorService 调度线程池}，
 * 因此也具有了ScheduledExecutorService提供的所有功能。
 * 问题：为什么需要继承ScheduledExecutorService（调度线程池），即为什么需要延时调度功能？
 *    因为在Netty中，有可能用户线程和Netty的I/O线程同时操作网络资源，而为了减少并发锁竞争，
 *    Netty将用户线程的任务包装成Netty的Task，然后像Netty的I/O任务一样去执行它们；
 *    有时候需要延时执行任务，或者周期性的执行任务，那么就需要调度功能；在Netty的设计上而言，为开发极大的简化编程方法。
 *
 * Will handle all the I/O operations for a {@link Channel} once registered.
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 *
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}
