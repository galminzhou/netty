/*
 * Copyright 2014 The Netty Project
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

/**
 * Creates a new {@link Channel}.
 */
@SuppressWarnings({ "ClassNameSameAsAncestorName", "deprecation" })
public interface ChannelFactory<T extends Channel> extends io.netty.bootstrap.ChannelFactory<T> {
    /**
     * 工厂模式：通过使用反射调用Channel的无参构造方法来创建Channel实例；
     *  1) 对于NioSocketChannel而言，由于它是充当客户端的功能，它的创建时机在连接 connect(...) 的时候；
     *  2) 对于NioServerSocketChannel而言，它充当服务端的功能，它的创建时机在绑定端口 bind(...) 的时候；
     * Creates a new channel.
     */
    @Override
    T newChannel();
}
