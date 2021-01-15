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
package io.netty.handler.timeout;

import io.netty.util.internal.PlatformDependent;

/**
 * 每次写，到写完结束超过设定时间就抛WriteTimeoutException；
 * WriteTimeoutException 可以通过覆盖 ChannelHandler 的 exceptionCaught(…) 方法检测到。
 *
 * A {@link TimeoutException} raised by {@link WriteTimeoutHandler} when a write operation
 * cannot finish in a certain period of time.
 */
public final class WriteTimeoutException extends TimeoutException {

    private static final long serialVersionUID = -144786655770296065L;

    public static final WriteTimeoutException INSTANCE = PlatformDependent.javaVersion() >= 7 ?
            new WriteTimeoutException(true) : new WriteTimeoutException();

    private WriteTimeoutException() { }

    private WriteTimeoutException(boolean shared) {
        super(shared);
    }
}
