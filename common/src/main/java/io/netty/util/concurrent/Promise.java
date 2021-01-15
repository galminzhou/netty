/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

/**
 * Promise 实例内部是一个任务，在Netty中任务的执行往往是异步的；通常是一个线程池来处理任务；
 *
 * 在Promise提供的{@link this#setSuccess(V result)} or {@link this#setFailure(Throwable t)}
 * 将来会被某个执行任务的线程在执行完成（success or failed）以后调用，
 * 同时，那个线程在调用 setSuccess(result) or setFailure(t) 后会调用 listeners的回调函数（当然，
 * 回调的具体内容不一定由执行任务的线程来自己来执行，它可以创建新的线程来执行或者也可以将回调任务提交到某个线程池来执行）；
 * 而且，一旦 setSuccess(result) or setFailure(t)后，那些 await() 或 sync() 的线程就会从等待中返回；
 *
 * 因此，就有两种编程实现方式：
 *  1) 一种是用await()，等待await()方法返回后，得到promise的执行结果，然后处理它；
 *  2) 另一种就是提供Listener实例，不太关心任务什么时候会执行完，只要它执行完了以后去执行listener中的处理方法就行；
 * Special {@link Future} which is writable.
 */
public interface Promise<V> extends Future<V> {

    /**
     * 标记此future成功及设置其执行结果，并且将通知所有的listeners；
     * 若此操作失败（失败是指此future已经有了结果了，成功的结果or失败的结果），将抛出异常；
     * Marks this future as a success and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setSuccess(V result);

    /**
     * 功能参考setSuccess(V)方法，区别在于如果操作失败，不会抛出异常，而是返回false；
     *
     * Marks this future as a success and notifies all listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean trySuccess(V result);

    /**
     * 标记此future失败及其失败的原因，并且通知所有的Listeners；
     * 若此操作失败（失败是指此future已经有了结果了），则抛出异常；
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * 功能参考setFailure(Throwable)，区别在于如果操作失败，不会抛出异常，而是返回false；
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    /**
     * 标记此future不可以被取消任务；
     * true: 表示当且仅当成功标记此future为不可取消或它已经完成而没有被取消时；
     * false:表示此future已经被取消；
     * Make this future impossible to cancel.
     *
     * @return {@code true} if and only if successfully marked this future as uncancellable or it is already done
     *         without being cancelled.  {@code false} if this future has been cancelled already.
     */
    boolean setUncancellable();

    /* **************** Override start ****************
     * 覆盖下列方法，使得它们的返回值为 Promise 类型；
     */

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptibly();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptibly();

    /* **************** Override end **************** */
}
