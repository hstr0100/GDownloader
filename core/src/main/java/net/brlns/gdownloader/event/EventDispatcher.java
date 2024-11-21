/*
 * Copyright (C) 2024 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.event;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class EventDispatcher {

    private static final Map<Class<?>, List<Handler>> handlers = new ConcurrentHashMap<>();

    public static <E extends IEvent> LambdaHandler<E> register(Class<E> eventType, Consumer<E> listener) {
        if (eventType == null || !IEvent.class.isAssignableFrom(eventType)) {
            throw new IllegalArgumentException("Consumer must handle a type that implements IEvent");
        }

        LambdaHandler<E> handler = new LambdaHandler<>(eventType, listener);

        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);

        return handler;
    }

    public static <E extends IEvent> void unregister(LambdaHandler<E> handler) {
        Class<E> eventType = handler.getEventType();
        if (eventType == null || !IEvent.class.isAssignableFrom(eventType)) {
            throw new IllegalArgumentException("Handler must handle a type that implements IEvent");
        }

        List<Handler> handlerList = handlers.get(eventType);

        if (handlerList != null) {
            handlerList.remove(handler);

            if (handlerList.isEmpty()) {
                handlers.remove(eventType);
            }
        }
    }

    public static <T extends IEventListener> void register(T listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventListener.class)
                || method.getParameterCount() != 1
                || !IEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }

            Class<?> eventType = method.getParameterTypes()[0];
            method.setAccessible(true);

            handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new MethodHandler(listener, method));
        }
    }

    public static <T extends IEventListener> void unregister(T listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventListener.class)
                || method.getParameterCount() != 1
                || !IEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }

            Class<?> eventType = method.getParameterTypes()[0];
            removeHandlerForListener(listener, eventType, method);
        }
    }

    private static <T extends IEventListener> void removeHandlerForListener(
        T listener, Class<?> eventType, Method method) {
        List<Handler> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            eventHandlers.removeIf((handler) -> {
                if (handler instanceof MethodHandler methodHandler) {
                    return methodHandler.getListener().equals(listener)
                        && methodHandler.getMethod().equals(method);
                }

                return false;
            });

            if (eventHandlers.isEmpty()) {
                handlers.remove(eventType);
            }
        }
    }

    public static void dispatchAsync(IEvent event) {
        dispatch(event, true);
    }

    public static void dispatch(IEvent event) {
        dispatch(event, false);
    }

    public static void dispatch(IEvent event, boolean async) {
        List<Handler> eventHandlers = handlers.getOrDefault(event.getClass(), Collections.emptyList());

        Runnable eventRunnable = () -> {
            for (Handler handler : eventHandlers) {
                try {
                    if (handler instanceof MethodHandler methodHandler) {
                        methodHandler.getMethod()
                            .invoke(methodHandler.getListener(), event);
                    } else if (handler instanceof LambdaHandler<?> lambdaHandler) {
                        @SuppressWarnings("unchecked")
                        Consumer<IEvent> consumer = (Consumer<IEvent>)lambdaHandler.getListener();
                        consumer.accept(event);
                    }
                } catch (Exception e) {
                    GDownloader.handleException(e);
                }
            }
        };
        if (async) {
            CompletableFuture.runAsync(eventRunnable);
        } else {
            eventRunnable.run();
        }
    }

    @Data
    public static class Handler {

    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LambdaHandler<E extends IEvent> extends Handler {

        private final Class<E> eventType;
        private final Consumer<E> listener;

    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MethodHandler extends Handler {

        private final IEventListener listener;
        private final Method method;
    }
}
